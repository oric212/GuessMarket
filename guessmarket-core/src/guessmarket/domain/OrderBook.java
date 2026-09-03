package guessmarket.domain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.PriorityQueue;

public final class OrderBook implements TradingMethod {
    private final boolean allowMint;
    private final int initial;
    private final int d;
    private final List<Option> options;
    private final Map<Option, OptionBook> booksByOption;
    private long nextSubmissionSequence;

    private static final Comparator<Order> BUY_PRIORITY = new BuyPriority();
    private static final Comparator<Order> SELL_PRIORITY = new SellPriority();

    public OrderBook(boolean allowMint, int initial, int d, List<Option> options) {
        if (initial < 0) {
            throw new IllegalArgumentException("Order Book initial amount cannot be negative");
        }
        if (d <= 0) {
            throw new IllegalArgumentException("Order Book d must be greater than zero");
        }
        if (options == null || options.size() != 2) {
            throw new IllegalArgumentException("An Order Book must contain exactly two options");
        }
        if (options.get(0) == null || options.get(1) == null || options.get(0) == options.get(1)) {
            throw new IllegalArgumentException("An Order Book must contain two distinct options");
        }

        this.allowMint = allowMint;
        this.initial = initial;
        this.d = d;
        this.options = List.copyOf(options);
        this.booksByOption = new IdentityHashMap<>();
        for (Option option : this.options) {
            booksByOption.put(option, new OptionBook());
        }
    }

    public boolean isMintAllowed() { return allowMint; }
    public int getInitial() { return initial; }
    public int getD() { return d; }
    public List<Option> getOptions() { return options; }

    public OrderMatchResult submitOrder(
            User user, Option option, OrderSide side, int quantity, double pricePerShare) {
        validateSubmission(user, option, side, quantity, pricePerShare);

        Order incoming = new Order(
                user, option, side, quantity, pricePerShare, nextSubmissionSequence++);
        OptionBook book = booksByOption.get(option);
        List<OrderExecution> executions = match(incoming, book);

        if (!incoming.isFilled()) {
            queueFor(book, side).add(incoming);
        }
        return new OrderMatchResult(incoming, executions);
    }

    public List<Order> getPendingBuyOrders(Option option) {
        return sortedOrders(requireBook(option).buys, BUY_PRIORITY);
    }

    public List<Order> getPendingSellOrders(Option option) {
        return sortedOrders(requireBook(option).sells, SELL_PRIORITY);
    }

    public OptionalDouble getLastExecutionPrice(Option option) {
        OptionBook book = requireBook(option);
        return book.lastExecutionPrice == null
                ? OptionalDouble.empty() : OptionalDouble.of(book.lastExecutionPrice);
    }

    public OptionalDouble getHighestBid(Option option) {
        Order order = requireBook(option).buys.peek();
        return order == null ? OptionalDouble.empty() : OptionalDouble.of(order.getPricePerShare());
    }

    public OptionalDouble getLowestAsk(Option option) {
        Order order = requireBook(option).sells.peek();
        return order == null ? OptionalDouble.empty() : OptionalDouble.of(order.getPricePerShare());
    }

    public OptionalDouble getMid(Option option) {
        OptionalDouble bid = getHighestBid(option);
        OptionalDouble ask = getLowestAsk(option);
        return bid.isPresent() && ask.isPresent()
                ? OptionalDouble.of((bid.getAsDouble() + ask.getAsDouble()) / 2.0)
                : OptionalDouble.empty();
    }

    public OptionalDouble getSpread(Option option) {
        OptionalDouble bid = getHighestBid(option);
        OptionalDouble ask = getLowestAsk(option);
        return bid.isPresent() && ask.isPresent()
                ? OptionalDouble.of(ask.getAsDouble() - bid.getAsDouble())
                : OptionalDouble.empty();
    }

    private List<OrderExecution> match(Order incoming, OptionBook book) {
        List<OrderExecution> executions = new ArrayList<>();
        PriorityQueue<Order> opposite = incoming.getSide() == OrderSide.BUY ? book.sells : book.buys;

        while (!incoming.isFilled() && !opposite.isEmpty() && crosses(incoming, opposite.peek())) {
            Order resting = opposite.peek();
            int quantity = Math.min(incoming.getRemainingQuantity(), resting.getRemainingQuantity());
            User buyer = incoming.getSide() == OrderSide.BUY ? incoming.getUser() : resting.getUser();
            User seller = incoming.getSide() == OrderSide.SELL ? incoming.getUser() : resting.getUser();
            double executionPrice = resting.getPricePerShare();

            incoming.fill(quantity);
            resting.fill(quantity);
            if (resting.isFilled()) opposite.remove();
            book.lastExecutionPrice = executionPrice;
            executions.add(new OrderExecution(buyer, seller, incoming.getOption(), quantity, executionPrice));
        }
        return List.copyOf(executions);
    }

    private boolean crosses(Order incoming, Order resting) {
        return incoming.getSide() == OrderSide.BUY
                ? incoming.getPricePerShare() >= resting.getPricePerShare()
                : incoming.getPricePerShare() <= resting.getPricePerShare();
    }

    private void validateSubmission(
            User user, Option option, OrderSide side, int quantity, double pricePerShare) {
        if (user == null) throw new IllegalArgumentException("Order user cannot be null");
        requireBook(option);
        if (side == null) throw new IllegalArgumentException("Order side cannot be null");
        if (quantity <= 0) throw new IllegalArgumentException("Order quantity must be greater than zero");
        if (!Double.isFinite(pricePerShare) || pricePerShare <= 0.0) {
            throw new IllegalArgumentException("Order price must be finite and greater than zero");
        }
        if (pricePerShare > d - 0.01) {
            throw new IllegalArgumentException("Order price cannot exceed d - 0.01");
        }
    }

    private OptionBook requireBook(Option option) {
        OptionBook book = booksByOption.get(option);
        if (book == null) throw new IllegalArgumentException("Option does not belong to this Order Book");
        return book;
    }

    private PriorityQueue<Order> queueFor(OptionBook book, OrderSide side) {
        return side == OrderSide.BUY ? book.buys : book.sells;
    }

    private List<Order> sortedOrders(PriorityQueue<Order> orders, Comparator<Order> priority) {
        return orders.stream().sorted(priority).toList();
    }

    private static final class OptionBook implements java.io.Serializable {
        private final PriorityQueue<Order> buys = new PriorityQueue<>(BUY_PRIORITY);
        private final PriorityQueue<Order> sells = new PriorityQueue<>(SELL_PRIORITY);
        private Double lastExecutionPrice;
    }

    private static final class BuyPriority implements Comparator<Order>, Serializable {
        @Override
        public int compare(Order first, Order second) {
            int price = Double.compare(second.getPricePerShare(), first.getPricePerShare());
            return price != 0 ? price : Long.compare(first.getSubmissionSequence(), second.getSubmissionSequence());
        }
    }

    private static final class SellPriority implements Comparator<Order>, Serializable {
        @Override
        public int compare(Order first, Order second) {
            int price = Double.compare(first.getPricePerShare(), second.getPricePerShare());
            return price != 0 ? price : Long.compare(first.getSubmissionSequence(), second.getSubmissionSequence());
        }
    }

    @Override
    public TradingMethodType getType() {
        return TradingMethodType.ORDER_BOOK;
    }
}
