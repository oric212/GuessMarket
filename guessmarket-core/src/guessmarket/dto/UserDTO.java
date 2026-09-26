package guessmarket.dto;

import java.util.List;

public record UserDTO(
        String username,
        double accountBalance,
        boolean blocked,
        boolean marketMaker,
        List<Integer> marketMakerEventIds,
        List<UserParticipationDTO> participations,
        List<AccountTransactionDTO> accountTransactions
) {
    public UserDTO(
            String username, double accountBalance, boolean blocked,
            List<Integer> marketMakerEventIds, List<UserParticipationDTO> participations) {
        this(username, accountBalance, blocked, !marketMakerEventIds.isEmpty(),
                marketMakerEventIds, participations, List.of());
    }

    public UserDTO {
        marketMakerEventIds = List.copyOf(marketMakerEventIds);
        participations = List.copyOf(participations);
        accountTransactions = List.copyOf(accountTransactions);
    }
}
