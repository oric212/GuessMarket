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
        holdingsByOption = Collections.unmodifiableMap(new LinkedHashMap<>(holdingsByOption));
        reservedSellByOption = Collections.unmodifiableMap(new LinkedHashMap<>(reservedSellByOption));
        availableToSellByOption = Collections.unmodifiableMap(new LinkedHashMap<>(availableToSellByOption));
        currentHoldingValueByOption = Collections.unmodifiableMap(
                new LinkedHashMap<>(currentHoldingValueByOption));
    }
}
