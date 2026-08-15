package guessmarket.dto;

public record OptionStateDTO(
    String optionName,
    double currentOptionValue,
    int quantityBought
){
}

