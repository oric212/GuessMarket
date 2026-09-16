package guessmarket.dto;

public record PendingOrderDTO(
        String username, String side, String option, int remainingQuantity, double pricePerShare) {
}
