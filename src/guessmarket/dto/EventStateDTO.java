package guessmarket.dto;

import java.util.List;

public record EventStateDTO(
    int id,
    String eventName,
    double currentEventAccountBalance,
    double totalCommissionCollected,
    List<OptionStateDTO> optionStateDTOList,
    List<TradeDTO> trades,
    String eventStatus
){
}
