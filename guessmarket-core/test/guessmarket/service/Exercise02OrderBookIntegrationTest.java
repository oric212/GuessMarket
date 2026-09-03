package guessmarket.service;

import guessmarket.domain.OrderSide;
import guessmarket.dto.OrderSubmissionResultDTO;
import guessmarket.dto.UserParticipationDTO;

import java.nio.file.Files;
import java.nio.file.Path;

public final class Exercise02OrderBookIntegrationTest {
    private static final double EPSILON = 1.0e-8;

    public static void main(String[] args) throws Exception {
        startupFundingAndInventory();
        startupFailuresAreAtomic();
        ordinaryExecutionAndCommission();
        reservationsAndRestingParticipation();
        autoMintConditionsAndPricing();
        autoMintCommissionAndBlocking();
        settlementAndLifecycle();
        System.out.println("Exercise02OrderBookIntegrationTest: all checks passed");
    }

    private static void startupFundingAndInventory() throws Exception {
        GuessMarketEngine engine = engine(market(false, "on-purchase", 10, 2, 10, 100, 20, 20));
        expectFailure(() -> engine.startEvent("Buyer", 1), "Only the assigned Market Maker");
        engine.startEvent(" mm ", 1);
        close(engine.getUser("MM").accountBalance(), 90, "Initial funding did not leave MM");
        close(engine.getEventState(1).currentEventAccountBalance(), 10, "Initial funding did not enter event");
        UserParticipationDTO mm = participation(engine, "MM");
        check(mm.holdingsByOption().get("Yes") == 5 && mm.holdingsByOption().get("No") == 5,
                "MM did not receive equal initial pair inventory");

        GuessMarketEngine zero = engine(market(false, "on-purchase", 0, 2, 0, 100, 20, 20));
        zero.startEvent("MM", 1);
        check(zero.getEventState(1).eventState().equals("ACTIVE"), "initial=0 should activate");
        close(zero.getEventState(1).currentEventAccountBalance(), 0, "Zero startup moved event money");
    }

    private static void startupFailuresAreAtomic() throws Exception {
        GuessMarketEngine poor = engine(market(false, "on-purchase", 10, 2, 0, 5, 20, 20));
        expectFailure(() -> poor.startEvent("MM", 1), "insufficient funds");
        close(poor.getUser("MM").accountBalance(), 5, "Failed startup changed MM balance");
        close(poor.getEventState(1).currentEventAccountBalance(), 0, "Failed startup funded event");
        check(poor.getEventState(1).eventState().equals("NOT_STARTED"), "Failed startup activated event");

        GuessMarketEngine indivisible = engine(market(false, "on-purchase", 10, 3, 0, 100, 20, 20));
        expectFailure(() -> indivisible.startEvent("MM", 1), "divisible by d");
        close(indivisible.getUser("MM").accountBalance(), 100, "Indivisible startup changed balance");
    }

    private static void ordinaryExecutionAndCommission() throws Exception {
        GuessMarketEngine engine = engine(market(false, "on-purchase", 10, 2, 10, 100, 20, 20));
        engine.startEvent("MM", 1);
        engine.submitOrder("MM", 1, 1, OrderSide.SELL, 3, 1.0);
        double eventBefore = engine.getEventState(1).currentEventAccountBalance();
        double buyerBefore = engine.getUser("Buyer").accountBalance();
        double mmBefore = engine.getUser("MM").accountBalance();

        OrderSubmissionResultDTO result = engine.submitOrder("Buyer", 1, 1, OrderSide.BUY, 3, 1.5);
        check(result.executions().size() == 1 && result.executions().getFirst().executionPrice() == 1.0,
                "Ordinary execution did not use resting price");
        close(engine.getUser("Buyer").accountBalance(), buyerBefore - 3.3, "Buyer payment is wrong");
        close(engine.getUser("MM").accountBalance(), mmBefore + 3.3, "Seller/MM receipt is wrong");
        close(engine.getEventState(1).currentEventAccountBalance(), eventBefore,
                "Ordinary trade changed event account");
        check(participation(engine, "Buyer").holdingsByOption().get("Yes") == 3,
                "Shares did not move to buyer");
        check(participation(engine, "MM").holdingsByOption().get("Yes") == 2,
                "Shares did not leave seller");
        close(participation(engine, "Buyer").totalCommissionPaid(), 0.3,
                "Purchase commission was not recorded");
    }

    private static void reservationsAndRestingParticipation() throws Exception {
        GuessMarketEngine engine = engine(market(false, "on-purchase", 10, 2, 0, 100, 20, 20));
        engine.startEvent("MM", 1);
        expectFailure(() -> engine.submitOrder("Buyer", 1, 1, OrderSide.SELL, 1, 1.0),
                "Insufficient available shares");
        engine.submitOrder("MM", 1, 1, OrderSide.SELL, 3, 1.5);
        UserParticipationDTO mm = participation(engine, "MM");
        check(mm.reservedSellByOption().get("Yes") == 3
                        && mm.availableToSellByOption().get("Yes") == 2,
                "Pending SELL reservation is wrong");
        expectFailure(() -> engine.submitOrder("MM", 1, 1, OrderSide.SELL, 3, 1.6),
                "Insufficient available shares");

        engine.submitOrder("Buyer", 1, 1, OrderSide.BUY, 1, 0.5);
        check(engine.getUser("Buyer").participations().size() == 1,
                "A resting order alone did not create participation");
    }

    private static void autoMintConditionsAndPricing() throws Exception {
        GuessMarketEngine disabled = engine(market(false, "on-purchase", 0, 1, 0, 100, 20, 20));
        disabled.startEvent("MM", 1);
        disabled.submitOrder("Buyer", 1, 1, OrderSide.BUY, 2, 0.6);
        OrderSubmissionResultDTO disabledResult = disabled.submitOrder("Other", 1, 2, OrderSide.BUY, 2, 0.5);
        check(disabledResult.mintExecutions().isEmpty() && disabledResult.remainingQuantity() == 2,
                "Mint occurred while disabled");

        GuessMarketEngine below = engine(market(true, "on-purchase", 0, 1, 0, 100, 20, 20));
        below.startEvent("MM", 1);
        below.submitOrder("Buyer", 1, 1, OrderSide.BUY, 2, 0.4);
        check(below.submitOrder("Other", 1, 2, OrderSide.BUY, 2, 0.5).mintExecutions().isEmpty(),
                "Mint occurred below d");

        GuessMarketEngine enabled = engine(market(true, "on-purchase", 0, 1, 0, 100, 20, 20));
        enabled.startEvent("MM", 1);
        enabled.submitOrder("Buyer", 1, 1, OrderSide.BUY, 3, 0.6);
        OrderSubmissionResultDTO minted = enabled.submitOrder("Other", 1, 2, OrderSide.BUY, 5, 0.55);
        check(minted.mintExecutions().size() == 1 && minted.mintExecutions().getFirst().quantity() == 3,
                "Mint did not use minimum quantity");
        close(minted.mintExecutions().getFirst().restingExecutionPrice(), 0.6, "Resting mint price is wrong");
        close(minted.mintExecutions().getFirst().incomingExecutionPrice(), 0.4,
                "Incoming complementary mint price is wrong");
        check(minted.remainingQuantity() == 2, "Unequal mint remainder is wrong");
        check(participation(enabled, "Buyer").holdingsByOption().get("Yes") == 3
                        && participation(enabled, "Other").holdingsByOption().get("No") == 3,
                "Minted shares were not allocated");
        close(enabled.getEventState(1).currentEventAccountBalance(), 3,
                "Mint pair value did not enter event account");
    }

    private static void autoMintCommissionAndBlocking() throws Exception {
        GuessMarketEngine mint = engine(market(true, "on-purchase", 0, 1, 10, 100, 20, 20));
        mint.startEvent("MM", 1);
        double mmBefore = mint.getUser("MM").accountBalance();
        mint.submitOrder("Buyer", 1, 1, OrderSide.BUY, 2, 0.6);
        mint.submitOrder("Other", 1, 2, OrderSide.BUY, 2, 0.5);
        close(mint.getUser("Buyer").accountBalance(), 20 - 1.32, "Resting mint buyer charge is wrong");
        close(mint.getUser("Other").accountBalance(), 20 - 0.88, "Incoming mint buyer charge is wrong");
        close(mint.getUser("MM").accountBalance(), mmBefore + 0.20, "Mint commissions did not reach MM");

        GuessMarketEngine blocked = engine(market(false, "on-purchase", 10, 2, 0, 100, 1, 20));
        blocked.startEvent("MM", 1);
        blocked.submitOrder("MM", 1, 1, OrderSide.SELL, 5, 1.0);
        blocked.submitOrder("Buyer", 1, 1, OrderSide.BUY, 5, 1.0);
        check(blocked.getUser("Buyer").blocked() && blocked.getUser("Buyer").accountBalance() < 0,
                "Negative ordinary buyer was not blocked");
        expectFailure(() -> blocked.submitOrder("Buyer", 1, 2, OrderSide.BUY, 1, 0.5), "is blocked");
    }

    private static void settlementAndLifecycle() throws Exception {
        GuessMarketEngine engine = engine(market(false, "on-close", 10, 2, 25, 100, 20, 20));
        engine.startEvent("MM", 1);
        engine.submitOrder("MM", 1, 1, OrderSide.SELL, 2, 1.0);
        engine.submitOrder("Buyer", 1, 1, OrderSide.BUY, 2, 1.0);
        engine.submitOrder("MM", 1, 2, OrderSide.SELL, 1, 1.0);
        engine.submitOrder("Other", 1, 2, OrderSide.BUY, 1, 1.0);
        expectFailure(() -> engine.closeEvent("Buyer", 1, 1), "Only the assigned Market Maker");
        double buyerBefore = engine.getUser("Buyer").accountBalance();
        double otherBefore = engine.getUser("Other").accountBalance();
        double mmBefore = engine.getUser("MM").accountBalance();

        engine.closeEvent("MM", 1, 1);
        close(engine.getUser("Buyer").accountBalance(), buyerBefore + 3.0,
                "Winner did not receive d per share less ON_CLOSE commission");
        close(engine.getUser("Other").accountBalance(), otherBefore, "Losing shares received a payout");
        close(engine.getUser("MM").accountBalance(), mmBefore + 7.0,
                "MM winning payout and close commissions are wrong");
        close(engine.getEventState(1).totalCommissionCollected(), 2.5,
                "ON_CLOSE commission total is wrong");
        close(engine.getEventState(1).currentEventAccountBalance(), 0,
                "OB settlement did not exhaust event account");
        expectFailure(() -> engine.submitOrder("Buyer", 1, 1, OrderSide.BUY, 1, 1), "Event must be active");
        expectFailure(() -> engine.startEvent("MM", 1), "only start from NOT_STARTED");
    }

    private static UserParticipationDTO participation(GuessMarketEngine engine, String username) {
        return engine.getUser(username).participations().stream()
                .filter(item -> item.eventId() == 1).findFirst()
                .orElseThrow(() -> new AssertionError("Missing participation for " + username));
    }

    private static GuessMarketEngine engine(String xml) throws Exception {
        Path file = Files.createTempFile("guessmarket-ob-integration-", ".xml");
        Files.writeString(file, xml);
        file.toFile().deleteOnExit();
        GuessMarketEngine engine = new GuessMarketEngine();
        engine.loadMarketFromXml(file.toString());
        return engine;
    }

    private static String market(boolean mint, String commissionType, int initial, int d,
                                 int commission, int mmCash, int buyerCash, int otherCash) {
        return "<Guess-Market><GM-events><GM-event name=\"OB\"><id>1</id><description>Test</description>" +
                "<commission type=\"" + commissionType + "\">" + commission + "</commission>" +
                "<GM-options><GM-option>Yes</GM-option><GM-option>No</GM-option></GM-options>" +
                "<GM-method><GM-order-book allow-mint=\"" + mint + "\" initial=\"" + initial +
                "\" d=\"" + d + "\"/></GM-method></GM-event></GM-events><GM-users>" +
                user("MM", mmCash, true) + user("Buyer", buyerCash, false) +
                user("Other", otherCash, false) + "</GM-users></Guess-Market>";
    }

    private static String user(String name, int cash, boolean mm) {
        return "<GM-user name=\"" + name + "\"><initial-cash>" + cash + "</initial-cash>" +
                (mm ? "<GM-market-maker><event id=\"1\"/></GM-market-maker>" : "") + "</GM-user>";
    }

    private static void expectFailure(Runnable action, String text) {
        try {
            action.run();
            throw new AssertionError("Expected failure containing: " + text);
        } catch (RuntimeException expected) {
            check(expected.getMessage() != null && expected.getMessage().contains(text),
                    "Unexpected error: " + expected.getMessage());
        }
    }

    private static void close(double actual, double expected, String message) {
        check(Math.abs(actual - expected) <= EPSILON, message + ": expected " + expected + ", got " + actual);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
