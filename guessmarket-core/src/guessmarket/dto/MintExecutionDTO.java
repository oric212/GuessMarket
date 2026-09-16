package guessmarket.dto;

public record MintExecutionDTO(
        String restingBuyerUsername, String incomingBuyerUsername,
        String restingOptionName, String incomingOptionName, int quantity,
        double restingExecutionPrice, double incomingExecutionPrice) {
}
