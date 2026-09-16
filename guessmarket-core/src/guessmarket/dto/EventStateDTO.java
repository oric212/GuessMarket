package guessmarket.dto;

import java.util.List;

public record EventStateDTO(
    int id,
    String eventName,
    double currentEventAccountBalance,
    double totalCommissionCollected,
    List<OptionStateDTO> optionStateDTOList,
    List<TradeDTO> trades,
    String eventState,
    String winningOption,
    String description,
    String tradingMethod,
    int commissionPercentage,
    String commissionMethod,
    String marketMakerUsername,
    List<String> options,
    LmsrDetailsDTO lmsrDetails,
    OrderBookDetailsDTO orderBookDetails,
    List<EventParticipantDTO> participants
){
    public EventStateDTO {
        optionStateDTOList = List.copyOf(optionStateDTOList);
        trades = List.copyOf(trades);
        options = List.copyOf(options);
        participants = List.copyOf(participants);
    }
}
