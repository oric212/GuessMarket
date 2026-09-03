package guessmarket.dto;

import java.util.List;
import java.util.Map;

public record UserParticipationDTO(
        int eventId,
        Map<String, Integer> holdingsByOption,
        Map<String, Integer> reservedSellByOption,
        Map<String, Integer> availableToSellByOption,
        List<TradeDTO> trades,
        double totalCommissionPaid,
        double totalCashPaid,
        double totalCashReceived) {
    public UserParticipationDTO {
        holdingsByOption = Map.copyOf(holdingsByOption);
        reservedSellByOption = Map.copyOf(reservedSellByOption);
        availableToSellByOption = Map.copyOf(availableToSellByOption);
        trades = List.copyOf(trades);
    }
}
