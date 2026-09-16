package guessmarket.dto;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record EventParticipantDTO(
        String username,
        Map<String, Integer> holdingsByOption,
        Map<String, Integer> reservedSellByOption,
        Map<String, Integer> availableToSellByOption,
        Map<String, Double> currentHoldingValueByOption,
        double totalCommissionPaid,
        double totalCashPaid,
        double totalCashReceived) {
    public EventParticipantDTO {
        holdingsByOption = Map.copyOf(holdingsByOption);
        reservedSellByOption = Map.copyOf(reservedSellByOption);
        availableToSellByOption = Map.copyOf(availableToSellByOption);
        currentHoldingValueByOption = Collections.unmodifiableMap(
                new LinkedHashMap<>(currentHoldingValueByOption));
    }
}
