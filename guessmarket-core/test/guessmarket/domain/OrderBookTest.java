package guessmarket.domain;

import java.util.List;
import java.util.OptionalDouble;

public final class OrderBookTest {
    private final Option yes = new Option("Yes");
    private final Option no = new Option("No");
    private final User alice = new User("Alice", 100);
    private final User bob = new User("Bob", 100);
    private final User carol = new User("Carol", 100);
    private final OrderBook book = new OrderBook(false, 0, 10, List.of(yes, no));

    public static void main(String[] args) {
        validation();
        restingOrdersAndOptionIsolation();
        exactAndCrossingPrice();
        partialFillsAndRemoval();
        consumesMultipleOrders();
        priceAndFifoPriority();
        statistics();
        System.out.println("OrderBookTest: all checks passed");
    }

    private static void validation() {
        OrderBookTest test = new OrderBookTest();
        expectFailure(() -> test.book.submitOrder(test.alice, test.yes, OrderSide.BUY, 0, 1), "quantity");
        expectFailure(() -> test.book.submitOrder(test.alice, test.yes, OrderSide.BUY, 1, 0), "price");
        expectFailure(() -> test.book.submitOrder(test.alice, test.yes, OrderSide.BUY, 1, Double.NaN), "price");
        expectFailure(() -> test.book.submitOrder(test.alice, test.yes, OrderSide.BUY, 1, Double.POSITIVE_INFINITY), "price");
        expectFailure(() -> test.book.submitOrder(test.alice, test.yes, OrderSide.BUY, 1, 10), "d - 0.01");
        expectFailure(() -> test.book.submitOrder(null, test.yes, OrderSide.BUY, 1, 1), "user");
        expectFailure(() -> test.book.submitOrder(test.alice, new Option("Foreign"), OrderSide.BUY, 1, 1), "belong");
        check(test.book.getPendingBuyOrders(test.yes).isEmpty(), "Invalid orders mutated the book");
    }

    private static void restingOrdersAndOptionIsolation() {
        OrderBookTest test = new OrderBookTest();
        OrderMatchResult buy = test.book.submitOrder(test.alice, test.yes, OrderSide.BUY, 3, 4);
        check(buy.executions().isEmpty(), "Buy matched without a seller");
        check(test.book.getPendingBuyOrders(test.yes).equals(List.of(buy.submittedOrder())), "Buy did not rest");

        OrderMatchResult sellOtherOption = test.book.submitOrder(test.bob, test.no, OrderSide.SELL, 3, 3);
        check(sellOtherOption.executions().isEmpty(), "Orders for different options matched");
        check(test.book.getPendingSellOrders(test.no).size() == 1, "Sell did not rest independently");
        check(test.book.getPendingSellOrders(test.yes).isEmpty(), "Other option leaked into this book");
    }

    private static void exactAndCrossingPrice() {
        OrderBookTest exact = new OrderBookTest();
        exact.book.submitOrder(exact.alice, exact.yes, OrderSide.SELL, 2, 5);
        OrderExecution exactExecution = only(exact.book.submitOrder(exact.bob, exact.yes, OrderSide.BUY, 2, 5));
        check(exactExecution.quantity() == 2 && exactExecution.executionPrice() == 5,
                "Exact-price execution is wrong");
        check(exactExecution.buyer() == exact.bob && exactExecution.seller() == exact.alice,
                "Execution parties are wrong");

        OrderBookTest crossingBuy = new OrderBookTest();
        crossingBuy.book.submitOrder(crossingBuy.alice, crossingBuy.yes, OrderSide.SELL, 1, 4);
        check(only(crossingBuy.book.submitOrder(crossingBuy.bob, crossingBuy.yes, OrderSide.BUY, 1, 6))
                .executionPrice() == 4, "Incoming BUY did not execute at resting SELL price");

        OrderBookTest crossingSell = new OrderBookTest();
        crossingSell.book.submitOrder(crossingSell.alice, crossingSell.yes, OrderSide.BUY, 1, 6);
        check(only(crossingSell.book.submitOrder(crossingSell.bob, crossingSell.yes, OrderSide.SELL, 1, 4))
                .executionPrice() == 6, "Incoming SELL did not execute at resting BUY price");
    }

    private static void partialFillsAndRemoval() {
        OrderBookTest partialResting = new OrderBookTest();
        Order restingSell = partialResting.book
                .submitOrder(partialResting.alice, partialResting.yes, OrderSide.SELL, 10, 5).submittedOrder();
        Order incomingBuy = partialResting.book
                .submitOrder(partialResting.bob, partialResting.yes, OrderSide.BUY, 4, 5).submittedOrder();
        check(incomingBuy.isFilled(), "Fully filled incoming order was not completed");
        check(restingSell.getRemainingQuantity() == 6, "Partial resting quantity is wrong");
        check(partialResting.book.getPendingBuyOrders(partialResting.yes).isEmpty(), "Completed order remained");
        check(partialResting.book.getPendingSellOrders(partialResting.yes).getFirst() == restingSell,
                "Partial resting order disappeared");

        OrderBookTest partialIncoming = new OrderBookTest();
        Order sell = partialIncoming.book
                .submitOrder(partialIncoming.alice, partialIncoming.yes, OrderSide.SELL, 4, 5).submittedOrder();
        Order buy = partialIncoming.book
                .submitOrder(partialIncoming.bob, partialIncoming.yes, OrderSide.BUY, 10, 5).submittedOrder();
        check(sell.isFilled() && buy.getRemainingQuantity() == 6, "Partial incoming fill is wrong");
        check(partialIncoming.book.getPendingSellOrders(partialIncoming.yes).isEmpty(), "Filled resting order remained");
        check(partialIncoming.book.getPendingBuyOrders(partialIncoming.yes).getFirst() == buy,
                "Partial incoming order did not rest");
    }

    private static void consumesMultipleOrders() {
        OrderBookTest test = new OrderBookTest();
        test.book.submitOrder(test.alice, test.yes, OrderSide.SELL, 2, 3);
        test.book.submitOrder(test.bob, test.yes, OrderSide.SELL, 3, 4);
        OrderMatchResult result = test.book.submitOrder(test.carol, test.yes, OrderSide.BUY, 6, 5);
        check(result.executions().size() == 2, "Incoming order did not consume multiple resting orders");
        check(result.executions().get(0).executionPrice() == 3
                        && result.executions().get(1).executionPrice() == 4,
                "Multiple executions ignored ask priority");
        check(result.submittedOrder().getRemainingQuantity() == 1, "Incoming remainder is wrong");
        check(test.book.getPendingSellOrders(test.yes).isEmpty(), "Completed sells remained");
    }

    private static void priceAndFifoPriority() {
        OrderBookTest bids = new OrderBookTest();
        bids.book.submitOrder(bids.alice, bids.yes, OrderSide.BUY, 1, 4);
        bids.book.submitOrder(bids.bob, bids.yes, OrderSide.BUY, 1, 6);
        check(only(bids.book.submitOrder(bids.carol, bids.yes, OrderSide.SELL, 1, 3)).buyer() == bids.bob,
                "Highest bid did not receive priority");

        OrderBookTest asks = new OrderBookTest();
        asks.book.submitOrder(asks.alice, asks.yes, OrderSide.SELL, 1, 6);
        asks.book.submitOrder(asks.bob, asks.yes, OrderSide.SELL, 1, 4);
        check(only(asks.book.submitOrder(asks.carol, asks.yes, OrderSide.BUY, 1, 7)).seller() == asks.bob,
                "Lowest ask did not receive priority");

        OrderBookTest fifo = new OrderBookTest();
        fifo.book.submitOrder(fifo.alice, fifo.yes, OrderSide.SELL, 1, 5);
        fifo.book.submitOrder(fifo.bob, fifo.yes, OrderSide.SELL, 1, 5);
        check(only(fifo.book.submitOrder(fifo.carol, fifo.yes, OrderSide.BUY, 1, 5)).seller() == fifo.alice,
                "FIFO priority failed at the same price");
    }

    private static void statistics() {
        OrderBookTest test = new OrderBookTest();
        check(test.book.getHighestBid(test.yes).isEmpty()
                && test.book.getLowestAsk(test.yes).isEmpty()
                && test.book.getMid(test.yes).isEmpty()
                && test.book.getSpread(test.yes).isEmpty()
                && test.book.getLastExecutionPrice(test.yes).isEmpty(), "Empty statistics should be unavailable");

        test.book.submitOrder(test.alice, test.yes, OrderSide.BUY, 2, 4);
        test.book.submitOrder(test.bob, test.yes, OrderSide.SELL, 2, 6);
        value(test.book.getHighestBid(test.yes), 4, "BID");
        value(test.book.getLowestAsk(test.yes), 6, "ASK");
        value(test.book.getMid(test.yes), 5, "MID");
        value(test.book.getSpread(test.yes), 2, "SPREAD");

        test.book.submitOrder(test.carol, test.yes, OrderSide.BUY, 1, 7);
        value(test.book.getLastExecutionPrice(test.yes), 6, "LAST");
        check(test.book.getLowestAsk(test.yes).orElseThrow() == 6,
                "Partially filled ask should remain visible");
    }

    private static OrderExecution only(OrderMatchResult result) {
        check(result.executions().size() == 1, "Expected exactly one execution");
        return result.executions().getFirst();
    }

    private static void value(OptionalDouble actual, double expected, String name) {
        check(actual.isPresent() && actual.getAsDouble() == expected, name + " statistic is wrong");
    }

    private static void expectFailure(Runnable action, String text) {
        try {
            action.run();
            throw new AssertionError("Expected failure containing: " + text);
        } catch (IllegalArgumentException expected) {
            check(expected.getMessage().toLowerCase().contains(text.toLowerCase()),
                    "Unexpected error: " + expected.getMessage());
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
