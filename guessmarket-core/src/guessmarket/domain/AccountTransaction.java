package guessmarket.domain;

import java.io.Serializable;

public record AccountTransaction(
        long sequence,
        AccountTransactionType type,
        double amountChange,
        double resultingBalance,
        String eventName,
        String description) implements Serializable {
}
