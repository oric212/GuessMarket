package guessmarket.dto;

public record OrderExecutionDTO(
        String buyerUsername, String sellerUsername, String optionName,
        int quantity, double executionPrice) {
}
