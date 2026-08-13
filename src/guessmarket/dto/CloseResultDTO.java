package guessmarket.dto;

import java.util.List;

public record CloseResultDTO(
        int totalQuantityBought,
        List<OptionStateDTO> optionStateDTOList,
        String winningOptionName
){
}


