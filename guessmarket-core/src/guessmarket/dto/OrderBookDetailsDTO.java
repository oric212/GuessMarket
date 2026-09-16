package guessmarket.dto;

import java.util.List;

public record OrderBookDetailsDTO(
        int d, boolean allowMint, int initial, List<OrderBookOptionDTO> optionBooks) {
    public OrderBookDetailsDTO {
        optionBooks = List.copyOf(optionBooks);
    }
}
