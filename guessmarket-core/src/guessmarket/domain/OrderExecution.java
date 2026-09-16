package guessmarket.domain;

import java.io.Serializable;

public record OrderExecution(
        User buyer,
        User seller,
        Option option,
        int quantity,
        double executionPrice
) implements Serializable {
    public OrderExecution {
        if (buyer == null || seller == null || option == null) {
            throw new IllegalArgumentException("Execution users and option cannot be null");
        }
        if (quantity <= 0 || !Double.isFinite(executionPrice) || executionPrice <= 0.0) {
            throw new IllegalArgumentException("Execution quantity and price must be positive");
        }
    }
}
