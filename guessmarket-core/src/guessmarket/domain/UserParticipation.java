package guessmarket.domain;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

public final class UserParticipation implements Serializable {
    private final List<Trade> trades = new ArrayList<>();
    private final Map<Option, Integer> quantitiesByOption = new LinkedHashMap<>();
    private double totalCommissionPaid;

    void record(Trade trade) {
        trades.add(trade);
        quantitiesByOption.merge(trade.getOption(), trade.getQuantity(), Integer::sum);
        totalCommissionPaid += trade.getCommissionPaid();
    }

    public List<Trade> getTrades() {
        return List.copyOf(trades);
    }

    public int getQuantity(Option option) {
        return quantitiesByOption.getOrDefault(option, 0);
    }

    public Map<Option, Integer> getQuantitiesByOption() {
        return Map.copyOf(quantitiesByOption);
    }

    public double getTotalCommissionPaid() {
        return totalCommissionPaid;
    }

    void addCloseCommission(double commission) {
        totalCommissionPaid += commission;
    }
}
