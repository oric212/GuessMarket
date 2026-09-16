package guessmarket.domain;

import java.io.Serializable;

public record PendingOrderSnapshot(
        String username, OrderSide side, String optionName,
        int remainingQuantity, double pricePerShare) implements Serializable {
}
