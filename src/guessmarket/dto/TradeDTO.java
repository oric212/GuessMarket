package guessmarket.dto;

public record TradeDTO(
        String boughtOptionName,
        int quantity,
        double purchaseCost
){
}
