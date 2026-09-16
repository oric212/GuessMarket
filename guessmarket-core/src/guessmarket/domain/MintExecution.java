package guessmarket.domain;

import java.io.Serializable;

public record MintExecution(User restingBuyer, User incomingBuyer,
        Option restingOption, Option incomingOption, int quantity,
        double restingExecutionPrice, double incomingExecutionPrice) implements Serializable {
}
