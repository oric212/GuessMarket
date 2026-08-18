package guessmarket.engine.market;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class Event implements Serializable {
    private final int id;
    private final String name;
    private final String description;
    private final int commissionPercentage;
    private final CommissionMethod commissionMethod;
    private final List<Option> options;
    private final LMSR tradingMethod;
    private final Account account;
    private final List<Trade> tradeHistory;

    private EventState state;
    private Option winningOption;
    private double totalCommissionCollected;

    public Event(
            int id,
            String name,
            String description,
            int commissionPercentage,
            CommissionMethod commissionMethod,
            LMSR tradingMethod,
            Account account) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Event name cannot be null or blank"
            );
        }

        if (description == null) {
            throw new IllegalArgumentException(
                    "Event description cannot be null"
            );
        }

        if (commissionPercentage < 0 || commissionPercentage > 90) {
            throw new IllegalArgumentException(
                    "Commission percentage must be between 0 and 90"
            );
        }

        if (commissionMethod == null) {
            throw new IllegalArgumentException(
                    "Commission method cannot be null"
            );
        }

        if (tradingMethod == null) {
            throw new IllegalArgumentException(
                    "Trading method cannot be null"
            );
        }

        if (account == null) {
            throw new IllegalArgumentException(
                    "Event account cannot be null"
            );
        }

        List<Option> eventOptions = List.copyOf(tradingMethod.getOptions());

        if (eventOptions.size() != 2) {
            throw new IllegalArgumentException(
                    "An Ex01 event must contain exactly two options"
            );
        }

        if (eventOptions.get(0) == eventOptions.get(1)) {
            throw new IllegalArgumentException(
                    "An event must contain two distinct options"
            );
        }

        this.id = id;
        this.name = name;
        this.description = description;
        this.commissionPercentage = commissionPercentage;
        this.commissionMethod = commissionMethod;
        this.options = eventOptions;
        this.tradingMethod = tradingMethod;
        this.account = account;
        this.tradeHistory = new ArrayList<>();
        this.state = EventState.ACTIVE;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getCommissionPercentage() {
        return commissionPercentage;
    }

    public CommissionMethod getCommissionMethod() {
        return commissionMethod;
    }

    public EventState getState() {
        return state;
    }

    public double getAccountBalance() {
        return account.getBalance();
    }

    public double getTotalCommissionCollected() {
        return totalCommissionCollected;
    }

    public Optional<Option> getWinningOption() {
        return Optional.ofNullable(winningOption);
    }

    public List<Option> getOptions() {
        return List.copyOf(options);
    }

    public List<Trade> getTradeHistory() {
        return List.copyOf(tradeHistory);
    }

    public Trade purchase(Option option, int quantity) {
        validateEventIsActive();
        validateOption(option);
        validateQuantity(quantity);

        double purchaseCost = tradingMethod.calculatePurchaseCost(option, quantity);

        if (!Double.isFinite(purchaseCost) || purchaseCost <= 0.0) {
            throw new IllegalStateException(
                    "LMSR produced an invalid purchase cost"
            );
        }

        double commissionPaid = 0.0;

        if (commissionMethod == CommissionMethod.ON_PURCHASE) {
            commissionPaid = purchaseCost * commissionPercentage / 100.0;
        }

        double totalPayment = purchaseCost + commissionPaid;
        double updatedCommissionTotal =
                totalCommissionCollected + commissionPaid;

        if (!Double.isFinite(totalPayment)
                || !Double.isFinite(updatedCommissionTotal)) {
            throw new IllegalStateException(
                    "Purchase produced an invalid monetary value"
            );
        }

        Trade trade = new Trade(
                option,
                quantity,
                purchaseCost,
                commissionPaid
        );

        account.deposit(totalPayment);
        tradingMethod.recordPurchase(option, quantity);
        tradeHistory.add(trade);
        totalCommissionCollected = updatedCommissionTotal;

        return trade;
    }

    public void close(Option winningOption) {
        validateEventIsActive();
        validateOption(winningOption);

        double grossPayout = 0.0;

        for (Trade trade : tradeHistory) {
            if (trade.getOption() == winningOption) {
                grossPayout += trade.getQuantity();
            }
        }

        double commissionAmount = 0.0;

        if (commissionMethod == CommissionMethod.ON_CLOSE) {
            commissionAmount = grossPayout * commissionPercentage / 100.0;
        }

        double netPayout = grossPayout - commissionAmount;
        double updatedCommissionTotal =
                totalCommissionCollected + commissionAmount;

        if (!Double.isFinite(netPayout)
                || !Double.isFinite(updatedCommissionTotal)) {
            throw new IllegalStateException(
                    "Settlement produced an invalid monetary value"
            );
        }

        account.withdraw(netPayout);

        totalCommissionCollected = updatedCommissionTotal;
        this.winningOption = winningOption;
        state = EventState.CLOSED;
    }

    private void validateOption(Option option) {

        if (!options.contains(option)) {
            throw new IllegalArgumentException("Option does not belong to this event");
        }
    }

    private void validateQuantity(int quantity) {

        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "Quantity must be greater than zero"
            );
        }
    }

    private void validateEventIsActive() {

        if (state != EventState.ACTIVE) {
            throw new IllegalStateException(
                    "Event must be active to perform this operation"
            );
        }
    }

    public int getQuantityBought(Option option) {
        validateOption(option);
        return tradingMethod.getQuantityBought(option);
    }

    public double getOptionPrice(Option option) {
        validateOption(option);
        return tradingMethod.calculateCurrentValue(option);
    }

    public Option getOptionByChoice(int optionChoice) {
        if (optionChoice < 1 || optionChoice > options.size()) {
            throw new IllegalArgumentException(
                    "Option choice must be between 1 and " + options.size()
            );
        }

        return options.get(optionChoice - 1);
    }
}
