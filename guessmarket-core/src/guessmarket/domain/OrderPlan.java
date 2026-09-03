package guessmarket.domain;

import java.util.List;

final class OrderPlan {
    record RestingFill(Order order, int quantity) {}
    private final long bookVersion;
    private final Order incomingOrder;
    private final int incomingFilledQuantity;
    private final List<RestingFill> restingFills;
    private final List<OrderExecution> ordinaryExecutions;
    private final List<MintExecution> mintExecutions;

    OrderPlan(long bookVersion, Order incomingOrder, int incomingFilledQuantity,
              List<RestingFill> restingFills, List<OrderExecution> ordinaryExecutions,
              List<MintExecution> mintExecutions) {
        this.bookVersion = bookVersion;
        this.incomingOrder = incomingOrder;
        this.incomingFilledQuantity = incomingFilledQuantity;
        this.restingFills = List.copyOf(restingFills);
        this.ordinaryExecutions = List.copyOf(ordinaryExecutions);
        this.mintExecutions = List.copyOf(mintExecutions);
    }
    long bookVersion() { return bookVersion; }
    Order incomingOrder() { return incomingOrder; }
    int incomingFilledQuantity() { return incomingFilledQuantity; }
    List<RestingFill> restingFills() { return restingFills; }
    List<OrderExecution> ordinaryExecutions() { return ordinaryExecutions; }
    List<MintExecution> mintExecutions() { return mintExecutions; }
}
