package guessmarket.dto;

import java.util.List;
import java.util.Map;

public record UserParticipationDTO(
        int eventId,
        Map<String, Integer> holdingsByOption,
        List<TradeDTO> trades,
        double totalCommissionPaid) {
    public UserParticipationDTO {
        holdingsByOption = Map.copyOf(holdingsByOption);
        trades = List.copyOf(trades);
    }
}
