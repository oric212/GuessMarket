package guessmarket.dto;

import guessmarket.domain.CommissionMethod;

import java.util.List;

public record CreateEventRequest(
        String creatorUsername,
        String eventName,
        String description,
        List<String> options,
        CommissionMethod commissionMethod,
        int commissionPercentage,
        TradingConfiguration tradingConfiguration) {

    public CreateEventRequest {
        options = options == null ? null : List.copyOf(options);
    }

    public sealed interface TradingConfiguration
            permits LmsrConfiguration, OrderBookConfiguration {
    }

    public record LmsrConfiguration(int liquidityParameter) implements TradingConfiguration {
    }

    public record OrderBookConfiguration(int d, int initial, boolean allowMint)
            implements TradingConfiguration {
    }
}
