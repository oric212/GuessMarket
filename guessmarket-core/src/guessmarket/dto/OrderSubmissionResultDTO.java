package guessmarket.dto;

import java.util.List;

public record OrderSubmissionResultDTO(
        int eventId, String optionName, String side, int originalQuantity,
        int remainingQuantity, double limitPrice,
        List<OrderExecutionDTO> executions, List<MintExecutionDTO> mintExecutions) {
    public OrderSubmissionResultDTO {
        executions = List.copyOf(executions);
        mintExecutions = List.copyOf(mintExecutions);
    }
}
