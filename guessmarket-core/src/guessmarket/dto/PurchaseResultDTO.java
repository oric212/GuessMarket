package guessmarket.dto;

public record PurchaseResultDTO(
        int id,
        String eventName,
        double totalPricePaid,
        double purchaseCost,
        double commission,
        String eventState
){
}
