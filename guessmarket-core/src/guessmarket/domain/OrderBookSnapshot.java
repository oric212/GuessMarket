package guessmarket.domain;

import java.io.Serializable;
import java.util.List;

public record OrderBookSnapshot(
        int d, boolean allowMint, int initial,
        List<OrderBookOptionSnapshot> optionBooks) implements Serializable {
    public OrderBookSnapshot {
        optionBooks = List.copyOf(optionBooks);
    }
}
