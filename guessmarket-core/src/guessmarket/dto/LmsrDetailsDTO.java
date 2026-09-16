package guessmarket.dto;

import java.util.List;

public record LmsrDetailsDTO(int liquidityParameter, List<OptionStateDTO> options, List<TradeDTO> trades) {
    public LmsrDetailsDTO {
        options = List.copyOf(options);
        trades = List.copyOf(trades);
    }
}
