package guessmarket.dto;

public record AccountTransactionDTO(
        long sequence,
        String type,
        double amountChange,
        double resultingBalance,
        String eventName,
        String description) {
}
