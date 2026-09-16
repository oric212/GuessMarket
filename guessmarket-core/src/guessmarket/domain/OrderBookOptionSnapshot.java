package guessmarket.domain;

import java.io.Serializable;
import java.util.List;
import java.util.OptionalDouble;

public record OrderBookOptionSnapshot(
        String optionName,
        List<PendingOrderSnapshot> pendingBuyOrders,
        List<PendingOrderSnapshot> pendingSellOrders,
        OptionalDouble last,
        OptionalDouble bid,
        OptionalDouble ask,
        OptionalDouble mid,
        OptionalDouble spread) implements Serializable {
    public OrderBookOptionSnapshot {
        pendingBuyOrders = List.copyOf(pendingBuyOrders);
        pendingSellOrders = List.copyOf(pendingSellOrders);
    }
}
