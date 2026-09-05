package guessmarket.domain;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

public final class UserParticipation implements Serializable {
    private final List<Trade> trades = new ArrayList<>();
    private final Map<Option, Integer> quantitiesByOption = new LinkedHashMap<>();
    private final Map<Option, Integer> reservedSellQuantities = new LinkedHashMap<>();
    private final Map<Option, Double> cumulativePurchaseAmounts = new LinkedHashMap<>();
    private double totalCommissionPaid;
    private double totalCashPaid;
    private double totalCashReceived;

    void record(Trade trade) {
        trades.add(trade);
        quantitiesByOption.merge(trade.getOption(), trade.getQuantity(), Integer::sum);
        totalCommissionPaid += trade.getCommissionPaid();
        totalCashPaid += trade.getPurchaseCost() + trade.getCommissionPaid();
        cumulativePurchaseAmounts.merge(trade.getOption(), trade.getPurchaseCost(), Double::sum);
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

    public int getReservedSellQuantity(Option option) {
        return reservedSellQuantities.getOrDefault(option, 0);
    }

    public int getAvailableToSell(Option option) {
        return getQuantity(option) - getReservedSellQuantity(option);
    }

    public double getTotalCashPaid() { return totalCashPaid; }
    public double getTotalCashReceived() { return totalCashReceived; }

    public double getCumulativePurchaseAmount(Option option) {
        return cumulativePurchaseAmounts.getOrDefault(option, 0.0);
    }

    void addHoldings(Option option, int quantity) {
        if (quantity > 0) quantitiesByOption.put(option, Math.addExact(getQuantity(option), quantity));
    }

    void removeHoldings(Option option, int quantity) {
        int current = getQuantity(option);
        if (quantity <= 0 || current < quantity) throw new IllegalStateException("Insufficient holdings");
        quantitiesByOption.put(option, current - quantity);
    }

    void reserveSell(Option option, int quantity) {
        if (quantity <= 0 || getAvailableToSell(option) < quantity) {
            throw new IllegalArgumentException("Insufficient available shares to sell");
        }
        reservedSellQuantities.merge(option, quantity, Integer::sum);
    }

    void releaseSell(Option option, int quantity) {
        int reserved = getReservedSellQuantity(option);
        if (quantity <= 0 || reserved < quantity) throw new IllegalStateException("Invalid sell reservation release");
        reservedSellQuantities.put(option, reserved - quantity);
    }

    void recordBuy(Option option, int quantity, double value, double commission) {
        addHoldings(option, quantity);
        totalCashPaid += value + commission;
        totalCommissionPaid += commission;
        cumulativePurchaseAmounts.merge(option, value, Double::sum);
    }

    void recordSell(Option option, int quantity, double value) {
        releaseSell(option, quantity);
        removeHoldings(option, quantity);
        totalCashReceived += value;
    }

    void recordSettlement(double netPayout, double commission) {
        totalCashReceived += netPayout;
        totalCommissionPaid += commission;
    }

    void recordInitialFunding(double amount) {
        totalCashPaid += amount;
    }
}
