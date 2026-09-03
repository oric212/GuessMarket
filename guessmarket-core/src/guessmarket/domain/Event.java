package guessmarket.domain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Event implements Serializable {
    private final int id;
    private final String name;
    private final String description;
    private final int commissionPercentage;
    private final CommissionMethod commissionMethod;
    private final List<Option> options;
    private final TradingMethod tradingMethod;
    private final Account account;
    private final List<Trade> tradeHistory;
    private final User marketMaker;
    private final Map<User, UserParticipation> participations;

    private EventState state;
    private Option winningOption;
    private double totalCommissionCollected;

    public Event(
            int id,
            String name,
            String description,
            int commissionPercentage,
            CommissionMethod commissionMethod,
            List<Option> options,
            TradingMethod tradingMethod,
            Account account,
            User marketMaker
            ) {

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

        if (options == null) {
            throw new IllegalArgumentException(
                    "Event options cannot be null"
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

        if (marketMaker == null) {
            throw new IllegalArgumentException(
                    "Market Maker cannot be null"
            );
        }

        List<Option> eventOptions = List.copyOf(options);

        if (eventOptions.size() != 2) {
            throw new IllegalArgumentException(
                    "An event must contain exactly two options"
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
        this.marketMaker = marketMaker;
        this.tradeHistory = new ArrayList<>();
        this.participations = new LinkedHashMap<>();
        this.state = EventState.NOT_STARTED;
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

    public EventState getState() {return state;}

    public TradingMethodType getTradingMethodType() {
        return tradingMethod.getType();
    }

    public boolean hasMarketMaker(User user) {
        return marketMaker == user;
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

    public void start(User actingUser) {
        validateMarketMaker(actingUser);
        actingUser.validateCanPerformActions();
        if (state != EventState.NOT_STARTED) {
            throw new IllegalStateException("Event can only start from NOT_STARTED");
        }
        if (tradingMethod instanceof OrderBook orderBook) {
            startOrderBook(actingUser, orderBook);
            return;
        }

        LMSR lmsr = (LMSR) tradingMethod;

        double subsidy = lmsr.calculateInitialSubsidy();
        if (!Double.isFinite(subsidy) || subsidy <= 0.0) {
            throw new IllegalStateException("LMSR produced an invalid initial subsidy");
        }
        if (!actingUser.canAfford(subsidy)) {
            throw new IllegalStateException("Market Maker has insufficient funds for the initial LMSR subsidy");
        }
        if (!account.canDeposit(subsidy)) {
            throw new IllegalStateException("Event account cannot accept the initial LMSR subsidy");
        }

        actingUser.withdraw(subsidy);
        account.deposit(subsidy);
        transitionTo(EventState.ACTIVE);
    }

    private void startOrderBook(User actingUser, OrderBook orderBook) {
        int initial = orderBook.getInitial();
        int d = orderBook.getD();
        if (initial % d != 0) {
            throw new IllegalStateException("Order Book initial amount must be divisible by d");
        }
        if (!actingUser.canAfford(initial)) {
            throw new IllegalStateException("Market Maker has insufficient funds for Order Book startup");
        }
        if (!account.canDeposit(initial)) {
            throw new IllegalStateException("Event account cannot accept Order Book initial funding");
        }
        int pairQuantity = initial / d;
        if (initial > 0) {
            actingUser.withdraw(initial);
            account.deposit(initial);
        }
        UserParticipation participation = participations.computeIfAbsent(
                marketMaker, ignored -> new UserParticipation());
        for (Option option : options) participation.addHoldings(option, pairQuantity);
        participation.recordInitialFunding(initial);
        transitionTo(EventState.ACTIVE);
    }

    public OrderMatchResult submitOrder(
            User actingUser, Option option, OrderSide side, int quantity, double pricePerShare) {
        if (actingUser == null) throw new IllegalArgumentException("Order user cannot be null");
        actingUser.validateCanPerformActions();
        validateTradingAllowed();
        validateOption(option);
        if (!(tradingMethod instanceof OrderBook orderBook)) {
            throw new IllegalStateException("Order submission is only supported for Order Book events");
        }
        UserParticipation submittingParticipation = participations.get(actingUser);
        if (side == OrderSide.SELL
                && (submittingParticipation == null
                || submittingParticipation.getAvailableToSell(option) < quantity)) {
            throw new IllegalArgumentException("Insufficient available shares to sell");
        }

        OrderPlan plan = orderBook.prepareOrder(actingUser, option, side, quantity, pricePerShare);
        Map<User, Double> balanceChanges = new LinkedHashMap<>();
        Map<ParticipantOption, Long> holdingChanges = new LinkedHashMap<>();
        double eventCredit = 0.0;
        double commissionTotal = 0.0;
        for (OrderExecution execution : plan.ordinaryExecutions()) {
            double value = execution.quantity() * execution.executionPrice();
            double commission = purchaseCommission(value);
            merge(balanceChanges, execution.buyer(), -value - commission);
            merge(balanceChanges, execution.seller(), value);
            merge(balanceChanges, marketMaker, commission);
            mergeHolding(holdingChanges, execution.buyer(), execution.option(), execution.quantity());
            mergeHolding(holdingChanges, execution.seller(), execution.option(), -execution.quantity());
            commissionTotal += commission;
        }
        for (MintExecution mint : plan.mintExecutions()) {
            double restingValue = mint.quantity() * mint.restingExecutionPrice();
            double incomingValue = mint.quantity() * mint.incomingExecutionPrice();
            double restingCommission = purchaseCommission(restingValue);
            double incomingCommission = purchaseCommission(incomingValue);
            merge(balanceChanges, mint.restingBuyer(), -restingValue - restingCommission);
            merge(balanceChanges, mint.incomingBuyer(), -incomingValue - incomingCommission);
            merge(balanceChanges, marketMaker, restingCommission + incomingCommission);
            eventCredit += (double) mint.quantity() * orderBook.getD();
            commissionTotal += restingCommission + incomingCommission;
            mergeHolding(holdingChanges, mint.restingBuyer(), mint.restingOption(), mint.quantity());
            mergeHolding(holdingChanges, mint.incomingBuyer(), mint.incomingOption(), mint.quantity());
        }
        if (!Double.isFinite(totalCommissionCollected + commissionTotal)
                || !account.canDeposit(eventCredit)) {
            throw new IllegalStateException("Order execution would produce an invalid monetary value");
        }
        for (Map.Entry<User, Double> change : balanceChanges.entrySet()) {
            if (!change.getKey().canApplyBalanceChange(change.getValue())) {
                throw new IllegalStateException("Order execution would produce an invalid account balance");
            }
        }
        for (Map.Entry<ParticipantOption, Long> change : holdingChanges.entrySet()) {
            UserParticipation participation = participations.get(change.getKey().user());
            int current = participation == null ? 0 : participation.getQuantity(change.getKey().option());
            long updated = current + change.getValue();
            if (updated < 0 || updated > Integer.MAX_VALUE) {
                throw new IllegalStateException("Order execution would produce invalid holdings");
            }
        }

        UserParticipation incomingParticipation = participations.computeIfAbsent(
                actingUser, ignored -> new UserParticipation());
        if (side == OrderSide.SELL) incomingParticipation.reserveSell(option, quantity);
        OrderMatchResult result = orderBook.commit(plan);
        for (Map.Entry<User, Double> change : balanceChanges.entrySet()) {
            change.getKey().applyBalanceChange(change.getValue());
        }
        if (eventCredit > 0.0) account.deposit(eventCredit);
        for (OrderExecution execution : result.executions()) {
            double value = execution.quantity() * execution.executionPrice();
            double commission = purchaseCommission(value);
            participations.computeIfAbsent(execution.buyer(), ignored -> new UserParticipation())
                    .recordBuy(execution.option(), execution.quantity(), value, commission);
            participations.get(execution.seller()).recordSell(
                    execution.option(), execution.quantity(), value);
        }
        for (MintExecution mint : result.mintExecutions()) {
            double restingValue = mint.quantity() * mint.restingExecutionPrice();
            double incomingValue = mint.quantity() * mint.incomingExecutionPrice();
            participations.computeIfAbsent(mint.restingBuyer(), ignored -> new UserParticipation())
                    .recordBuy(mint.restingOption(), mint.quantity(), restingValue, purchaseCommission(restingValue));
            participations.computeIfAbsent(mint.incomingBuyer(), ignored -> new UserParticipation())
                    .recordBuy(mint.incomingOption(), mint.quantity(), incomingValue, purchaseCommission(incomingValue));
        }
        totalCommissionCollected += commissionTotal;
        return result;
    }

    private double purchaseCommission(double value) {
        return commissionMethod == CommissionMethod.ON_PURCHASE
                ? value * commissionPercentage / 100.0 : 0.0;
    }

    private void merge(Map<User, Double> changes, User user, double amount) {
        changes.merge(user, amount, Double::sum);
    }

    private void mergeHolding(
            Map<ParticipantOption, Long> changes, User user, Option option, long quantity) {
        changes.merge(new ParticipantOption(user, option), quantity, Long::sum);
    }

    private record ParticipantOption(User user, Option option) {}

    public Trade purchaseLmsrShares(User buyer, Option option, int quantity) {
        if (buyer == null) throw new IllegalArgumentException("Buyer cannot be null");
        buyer.validateCanPerformActions();
        validateTradingAllowed();
        validateOption(option);
        validateQuantity(quantity);

        if (!(tradingMethod instanceof LMSR lmsr)) {
            throw new IllegalStateException(
                    "This operation is only supported for LMSR events"
            );
        }

        double purchaseCost =
                lmsr.calculatePurchaseCost(option, quantity);

        if (!Double.isFinite(purchaseCost) || purchaseCost <= 0.0) {
            throw new IllegalStateException(
                    "LMSR produced an invalid purchase cost"
            );
        }

        double commissionPaid = 0.0;

        if (commissionMethod == CommissionMethod.ON_PURCHASE) {
            commissionPaid =
                    purchaseCost * commissionPercentage / 100.0;
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

        if (!account.canDeposit(purchaseCost)
                || (commissionPaid > 0.0 && !marketMaker.canReceive(commissionPaid))) {
            throw new IllegalStateException("Purchase would produce an invalid account balance");
        }
        buyer.withdraw(totalPayment);
        account.deposit(purchaseCost);
        if (commissionPaid > 0.0) {
            marketMaker.deposit(commissionPaid);
        }
        lmsr.recordPurchase(option, quantity);
        tradeHistory.add(trade);
        participations.computeIfAbsent(buyer, ignored -> new UserParticipation()).record(trade);
        totalCommissionCollected = updatedCommissionTotal;

        return trade;
    }

    public void close(User actingUser, Option winningOption) {
        validateMarketMaker(actingUser);
        actingUser.validateCanPerformActions();
        validateTradingAllowed();
        validateOption(winningOption);
        if (tradingMethod instanceof OrderBook orderBook) {
            closeOrderBook(winningOption, orderBook);
            return;
        }

        Map<User, Settlement> settlements = new LinkedHashMap<>();
        double totalGross = 0.0;
        double totalCloseCommission = 0.0;
        for (Map.Entry<User, UserParticipation> entry : participations.entrySet()) {
            double gross = entry.getValue().getQuantity(winningOption);
            double commission = commissionMethod == CommissionMethod.ON_CLOSE
                    ? gross * commissionPercentage / 100.0 : 0.0;
            settlements.put(entry.getKey(), new Settlement(gross - commission, commission));
            totalGross += gross;
            totalCloseCommission += commission;
        }
        if (!Double.isFinite(totalGross) || account.getBalance() + 1.0e-9 < totalGross) {
            throw new IllegalStateException("Event account cannot cover LMSR settlement");
        }
        double mmCredit = totalCloseCommission + Math.max(0.0, account.getBalance() - totalGross);
        Settlement marketMakerSettlement = settlements.get(marketMaker);
        if (marketMakerSettlement != null) mmCredit += marketMakerSettlement.netPayout;
        if (!Double.isFinite(totalCommissionCollected + totalCloseCommission)
                || !marketMaker.canReceive(mmCredit)) {
            throw new IllegalStateException("Settlement would produce an invalid account balance");
        }
        for (Map.Entry<User, Settlement> entry : settlements.entrySet()) {
            if (entry.getKey() != marketMaker && !entry.getKey().canReceive(entry.getValue().netPayout)) {
                throw new IllegalStateException("Settlement would produce an invalid participant balance");
            }
        }

        for (Map.Entry<User, Settlement> entry : settlements.entrySet()) {
            Settlement settlement = entry.getValue();
            if (settlement.netPayout > 0.0) entry.getKey().deposit(settlement.netPayout);
            if (settlement.commission > 0.0) {
                marketMaker.deposit(settlement.commission);
                participations.get(entry.getKey()).recordSettlement(
                        settlement.netPayout, settlement.commission);
            } else if (settlement.netPayout > 0.0) {
                participations.get(entry.getKey()).recordSettlement(settlement.netPayout, 0.0);
            }
        }
        account.withdraw(totalGross);
        double remainder = account.drain();
        if (remainder > 0.0) marketMaker.deposit(remainder);

        totalCommissionCollected += totalCloseCommission;
        this.winningOption = winningOption;

        transitionTo(EventState.CLOSED);
    }

    private void closeOrderBook(Option winningOption, OrderBook orderBook) {
        Map<User, Settlement> settlements = new LinkedHashMap<>();
        double totalGross = 0.0;
        double totalCommission = 0.0;
        for (Map.Entry<User, UserParticipation> entry : participations.entrySet()) {
            double gross = (double) entry.getValue().getQuantity(winningOption) * orderBook.getD();
            double commission = commissionMethod == CommissionMethod.ON_CLOSE
                    ? gross * commissionPercentage / 100.0 : 0.0;
            settlements.put(entry.getKey(), new Settlement(gross - commission, commission));
            totalGross += gross;
            totalCommission += commission;
        }
        if (!Double.isFinite(totalGross) || Math.abs(account.getBalance() - totalGross) > 1.0e-8) {
            throw new IllegalStateException("Order Book event account does not exactly back issued share pairs");
        }
        Map<User, Double> changes = new LinkedHashMap<>();
        for (Map.Entry<User, Settlement> entry : settlements.entrySet()) {
            merge(changes, entry.getKey(), entry.getValue().netPayout);
            merge(changes, marketMaker, entry.getValue().commission);
        }
        if (!Double.isFinite(totalCommissionCollected + totalCommission)) {
            throw new IllegalStateException("Order Book settlement produced an invalid commission total");
        }
        for (Map.Entry<User, Double> change : changes.entrySet()) {
            if (!change.getKey().canApplyBalanceChange(change.getValue())) {
                throw new IllegalStateException("Order Book settlement would produce an invalid account balance");
            }
        }
        for (Map.Entry<User, Double> change : changes.entrySet()) {
            change.getKey().applyBalanceChange(change.getValue());
        }
        for (Map.Entry<User, Settlement> entry : settlements.entrySet()) {
            participations.get(entry.getKey()).recordSettlement(
                    entry.getValue().netPayout, entry.getValue().commission);
        }
        if (totalGross > 0.0) account.withdraw(totalGross);
        account.drain();
        totalCommissionCollected += totalCommission;
        this.winningOption = winningOption;
        transitionTo(EventState.CLOSED);
    }

    public Optional<UserParticipation> getParticipation(User user) {
        return Optional.ofNullable(participations.get(user));
    }

    private void validateMarketMaker(User actingUser) {
        if (actingUser == null || marketMaker != actingUser) {
            throw new IllegalArgumentException("Only the assigned Market Maker may perform this operation");
        }
    }

    private record Settlement(double netPayout, double commission) {}

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

    private void validateTradingAllowed() {
        if (!state.allowsTrading()) {
            throw new IllegalStateException(
                    "Event must be active to perform trading operations"
            );
        }
    }

    public int getLmsrQuantityBought(Option option) {
        validateOption(option);

        LMSR lmsr = getLmsr();

        return lmsr.getQuantityBought(option);
    }

    private LMSR getLmsr() {
        if (!(tradingMethod instanceof LMSR lmsr)) {
            throw new IllegalStateException(
                    "Quantity bought is only available for LMSR events"
            );
        }
        return lmsr;
    }

    public double getLmsrOptionPrice(Option option) {
        validateOption(option);

        LMSR lmsr = getLmsr();

        return lmsr.calculateCurrentValue(option);
    }

    public Option getOptionByChoice(int optionChoice) {
        if (optionChoice < 1 || optionChoice > options.size()) {
            throw new IllegalArgumentException(
                    "Option choice must be between 1 and " + options.size()
            );
        }

        return options.get(optionChoice - 1);
    }

    private void transitionTo(EventState nextState){
        if(!state.canTransitionTo(nextState)) {
            throw new IllegalStateException("Cannot transition event from " + state + " to " + nextState);
        }
        state = nextState;
    }
}
