package guessmarket.domain;

import java.io.Serializable;
import java.util.List;

public record OrderMatchResult(
        Order submittedOrder,
        List<OrderExecution> executions
) implements Serializable {
    public OrderMatchResult {
        if (submittedOrder == null) {
            throw new IllegalArgumentException("Submitted order cannot be null");
        }
        executions = List.copyOf(executions);
    }
}
