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
    private long bookVersion;
    private static final Comparator<Order> BUY_PRIORITY = new BuyPriority();
    private static final Comparator<Order> SELL_PRIORITY = new SellPriority();

    public OrderBook(boolean allowMint, int initial, int d, List<Option> options) {
        if (initial < 0) throw new IllegalArgumentException("Order Book initial amount cannot be negative");
        if (d <= 0) throw new IllegalArgumentException("Order Book d must be greater than zero");
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
        for (Option option : this.options) booksByOption.put(option, new OptionBook());
    }

    public boolean isMintAllowed() { return allowMint; }
    public int getInitial() { return initial; }
    public int getD() { return d; }
    public List<Option> getOptions() { return options; }

    public OrderMatchResult submitOrder(
            User user, Option option, OrderSide side, int quantity, double pricePerShare) {
        return commit(prepareOrder(user, option, side, quantity, pricePerShare));
    }

    OrderPlan prepareOrder(User user, Option option, OrderSide side, int quantity, double pricePerShare) {
        validateSubmission(user, option, side, quantity, pricePerShare);
        Order incoming = new Order(user, option, side, quantity, pricePerShare, nextSubmissionSequence);
        List<OrderPlan.RestingFill> fills = new ArrayList<>();
        List<OrderExecution> ordinary = new ArrayList<>();
        List<MintExecution> mints = new ArrayList<>();
        Map<Order, Integer> virtualRemaining = new IdentityHashMap<>();
        int remaining = quantity;

        List<Order> opposite = side == OrderSide.BUY
                ? getPendingSellOrders(option) : getPendingBuyOrders(option);
        for (Order resting : opposite) {
            if (remaining == 0 || !crosses(incoming, resting)) break;
            int fill = Math.min(remaining, resting.getRemainingQuantity());
            fills.add(new OrderPlan.RestingFill(resting, fill));
            virtualRemaining.put(resting, resting.getRemainingQuantity() - fill);
            User buyer = side == OrderSide.BUY ? user : resting.getUser();
            User seller = side == OrderSide.SELL ? user : resting.getUser();
            ordinary.add(new OrderExecution(buyer, seller, option, fill, resting.getPricePerShare()));
            remaining -= fill;
        }

        if (allowMint && side == OrderSide.BUY && remaining > 0) {
            Option oppositeOption = option == options.get(0) ? options.get(1) : options.get(0);
            for (Order resting : getPendingBuyOrders(oppositeOption)) {
                if (remaining == 0) break;
                int restingRemaining = virtualRemaining.getOrDefault(resting, resting.getRemainingQuantity());
                if (restingRemaining == 0 || pricePerShare + resting.getPricePerShare() < d) continue;
                int fill = Math.min(remaining, restingRemaining);
                fills.add(new OrderPlan.RestingFill(resting, fill));
                virtualRemaining.put(resting, restingRemaining - fill);
                mints.add(new MintExecution(resting.getUser(), user, oppositeOption, option, fill,
                        resting.getPricePerShare(), d - resting.getPricePerShare()));
                remaining -= fill;
            }
        }
        return new OrderPlan(bookVersion, incoming, quantity - remaining, fills, ordinary, mints);
    }

    OrderMatchResult commit(OrderPlan plan) {
        if (plan.bookVersion() != bookVersion
                || plan.incomingOrder().getSubmissionSequence() != nextSubmissionSequence) {
            throw new IllegalStateException("Order Book changed after the order plan was prepared");
        }
        for (OrderPlan.RestingFill fill : plan.restingFills()) {
            fill.order().fill(fill.quantity());
            if (fill.order().isFilled()) {
                queueFor(requireBook(fill.order().getOption()), fill.order().getSide()).remove(fill.order());
            }
        }
        if (plan.incomingFilledQuantity() > 0) plan.incomingOrder().fill(plan.incomingFilledQuantity());
        if (!plan.incomingOrder().isFilled()) {
            queueFor(requireBook(plan.incomingOrder().getOption()), plan.incomingOrder().getSide())
                    .add(plan.incomingOrder());
        }
        for (OrderExecution execution : plan.ordinaryExecutions()) {
            requireBook(execution.option()).lastExecutionPrice = execution.executionPrice();
        }
        nextSubmissionSequence++;
        bookVersion++;
        return new OrderMatchResult(plan.incomingOrder(), plan.ordinaryExecutions(), plan.mintExecutions());
    }

    public List<Order> getPendingBuyOrders(Option option) {
        return sortedOrders(requireBook(option).buys, BUY_PRIORITY);
    }
    public List<Order> getPendingSellOrders(Option option) {
        return sortedOrders(requireBook(option).sells, SELL_PRIORITY);
    }
    public OptionalDouble getLastExecutionPrice(Option option) {
        Double last = requireBook(option).lastExecutionPrice;
        return last == null ? OptionalDouble.empty() : OptionalDouble.of(last);
    }
    public OptionalDouble getHighestBid(Option option) { return priceOf(requireBook(option).buys.peek()); }
    public OptionalDouble getLowestAsk(Option option) { return priceOf(requireBook(option).sells.peek()); }
    public OptionalDouble getMid(Option option) {
        OptionalDouble bid = getHighestBid(option), ask = getLowestAsk(option);
        return bid.isPresent() && ask.isPresent()
                ? OptionalDouble.of((bid.getAsDouble() + ask.getAsDouble()) / 2.0) : OptionalDouble.empty();
    }
    public OptionalDouble getSpread(Option option) {
        OptionalDouble bid = getHighestBid(option), ask = getLowestAsk(option);
        return bid.isPresent() && ask.isPresent()
                ? OptionalDouble.of(ask.getAsDouble() - bid.getAsDouble()) : OptionalDouble.empty();
    }

    public OrderBookSnapshot snapshot() {
        return new OrderBookSnapshot(d, allowMint, initial, options.stream().map(option ->
                new OrderBookOptionSnapshot(
                        option.getName(),
                        getPendingBuyOrders(option).stream().map(this::snapshot).toList(),
                        getPendingSellOrders(option).stream().map(this::snapshot).toList(),
                        getLastExecutionPrice(option), getHighestBid(option), getLowestAsk(option),
                        getMid(option), getSpread(option))).toList());
    }

    private PendingOrderSnapshot snapshot(Order order) {
        return new PendingOrderSnapshot(order.getUser().getUsername(), order.getSide(),
                order.getOption().getName(), order.getRemainingQuantity(), order.getPricePerShare());
    }

    private OptionalDouble priceOf(Order order) {
        return order == null ? OptionalDouble.empty() : OptionalDouble.of(order.getPricePerShare());
    }
    private boolean crosses(Order incoming, Order resting) {
        return incoming.getSide() == OrderSide.BUY
                ? incoming.getPricePerShare() >= resting.getPricePerShare()
                : incoming.getPricePerShare() <= resting.getPricePerShare();
    }
    private void validateSubmission(User user, Option option, OrderSide side, int quantity, double price) {
        if (user == null) throw new IllegalArgumentException("Order user cannot be null");
        requireBook(option);
        if (side == null) throw new IllegalArgumentException("Order side cannot be null");
        if (quantity <= 0) throw new IllegalArgumentException("Order quantity must be greater than zero");
        if (!Double.isFinite(price) || price <= 0.0) {
            throw new IllegalArgumentException("Order price must be finite and greater than zero");
        }
        if (price > d - 0.01) throw new IllegalArgumentException("Order price cannot exceed d - 0.01");
    }
    private OptionBook requireBook(Option option) {
        OptionBook book = booksByOption.get(option);
        if (book == null) throw new IllegalArgumentException("Option does not belong to this Order Book");
        return book;
    }
    private PriorityQueue<Order> queueFor(OptionBook book, OrderSide side) {
        return side == OrderSide.BUY ? book.buys : book.sells;
    }
    private List<Order> sortedOrders(PriorityQueue<Order> orders, Comparator<Order> comparator) {
        return orders.stream().sorted(comparator).toList();
    }

    private static final class OptionBook implements Serializable {
        private final PriorityQueue<Order> buys = new PriorityQueue<>(BUY_PRIORITY);
        private final PriorityQueue<Order> sells = new PriorityQueue<>(SELL_PRIORITY);
        private Double lastExecutionPrice;
    }
    private static final class BuyPriority implements Comparator<Order>, Serializable {
        public int compare(Order a, Order b) {
            int price = Double.compare(b.getPricePerShare(), a.getPricePerShare());
            return price != 0 ? price : Long.compare(a.getSubmissionSequence(), b.getSubmissionSequence());
        }
    }
    private static final class SellPriority implements Comparator<Order>, Serializable {
        public int compare(Order a, Order b) {
            int price = Double.compare(a.getPricePerShare(), b.getPricePerShare());
            return price != 0 ? price : Long.compare(a.getSubmissionSequence(), b.getSubmissionSequence());
        }
    }
    @Override public TradingMethodType getType() { return TradingMethodType.ORDER_BOOK; }
}
