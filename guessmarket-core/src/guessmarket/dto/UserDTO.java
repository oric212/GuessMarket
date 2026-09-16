package guessmarket.dto;

import java.util.List;

public record UserDTO(
        String username,
        double accountBalance,
        boolean blocked,
        List<Integer> marketMakerEventIds,
        List<UserParticipationDTO> participations
) {
    public UserDTO {
        marketMakerEventIds = List.copyOf(marketMakerEventIds);
        participations = List.copyOf(participations);
    }
}
