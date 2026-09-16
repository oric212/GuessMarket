package guessmarket.dto;

import java.util.List;

public record OrderBookOptionDTO(
        String optionName,
        List<PendingOrderDTO> pendingBuyOrders,
        List<PendingOrderDTO> pendingSellOrders,
        Double last,
        Double bid,
        Double ask,
        Double mid,
        Double spread) {
    public OrderBookOptionDTO {
        pendingBuyOrders = List.copyOf(pendingBuyOrders);
        pendingSellOrders = List.copyOf(pendingSellOrders);
    }
}
