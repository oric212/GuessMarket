package guessmarket.dto;

import java.util.List;
import java.util.Map;

public record UserParticipationDTO(
        int eventId,
        String eventName,
        String tradingMethod,
        String eventState,
        String winningOption,
        Map<String, Integer> holdingsByOption,
        Map<String, Integer> reservedSellByOption,
        Map<String, Integer> availableToSellByOption,
        List<TradeDTO> trades,
        Map<String, Double> cumulativePurchaseAmountByOption,
        Map<String, Double> currentHoldingValueByOption,
        double totalCommissionPaid,
        double totalCashPaid,
        double totalCashReceived,
        Double profitLoss) {
    public UserParticipationDTO {
        holdingsByOption = Map.copyOf(holdingsByOption);
        reservedSellByOption = Map.copyOf(reservedSellByOption);
        availableToSellByOption = Map.copyOf(availableToSellByOption);
        trades = List.copyOf(trades);
        cumulativePurchaseAmountByOption = Map.copyOf(cumulativePurchaseAmountByOption);
        currentHoldingValueByOption = java.util.Collections.unmodifiableMap(
                new java.util.LinkedHashMap<>(currentHoldingValueByOption));
    }
}
