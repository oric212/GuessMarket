package guessmarket.service;

import guessmarket.dto.EventStateDTO;
import guessmarket.dto.PurchaseResultDTO;
import guessmarket.dto.UserParticipationDTO;

import java.nio.file.Files;
import java.nio.file.Path;

public final class Exercise02LifecycleTest {
    private static final double EPSILON = 1.0e-8;

    public static void main(String[] args) throws Exception {
        startAuthorizationFundingAndLifecycle();
        insufficientFundsAndOrderBookRejectionAreAtomic();
        purchasesMoneyFlowAndIndependentParticipation();
        negativeBalanceBlocksFurtherActions();
        closeAuthorizationAndOnPurchaseSettlement();
        onCloseSettlementAndBlockedPassiveCredit();
        System.out.println("Exercise02LifecycleTest: all checks passed");
    }

    private static void startAuthorizationFundingAndLifecycle() throws Exception {
        GuessMarketEngine engine = engine(lmsrMarket("on-purchase", 5, 100, 500, 100, false));
        expectFailure(() -> engine.startEvent("Buyer", 1), "Only the assigned Market Maker");
        check(engine.getEventState(1).eventState().equals("NOT_STARTED"), "Wrong user changed state");

        double subsidy = 100.0 * Math.log(2.0);
        EventStateDTO started = engine.startEvent(" mm ", 1);
        close(started.currentEventAccountBalance(), subsidy, "Subsidy did not enter event account");
        close(engine.getUser("MM").accountBalance(), 500.0 - subsidy, "Subsidy did not leave MM");
        check(started.eventState().equals("ACTIVE"), "Correct MM did not activate event");
        expectFailure(() -> engine.startEvent("MM", 1), "only start from NOT_STARTED");
    }

    private static void insufficientFundsAndOrderBookRejectionAreAtomic() throws Exception {
        GuessMarketEngine poor = engine(lmsrMarket("on-purchase", 5, 100, 10, 100, false));
        expectFailure(() -> poor.startEvent("MM", 1), "insufficient funds");
        close(poor.getUser("MM").accountBalance(), 10.0, "Failed startup changed MM balance");
        close(poor.getEventState(1).currentEventAccountBalance(), 0.0, "Failed startup funded event");
        check(poor.getEventState(1).eventState().equals("NOT_STARTED"), "Failed startup changed state");

        GuessMarketEngine orderBook = engine(orderBookMarket());
        orderBook.startEvent("MM", 1);
        check(orderBook.getEventState(1).eventState().equals("ACTIVE"), "OB event did not start");
    }

    private static void purchasesMoneyFlowAndIndependentParticipation() throws Exception {
        GuessMarketEngine engine = engine(lmsrMarket("on-purchase", 10, 100, 500, 200, false));
        engine.startEvent("MM", 1);
        double buyerBefore = engine.getUser("Buyer").accountBalance();
        double mmBefore = engine.getUser("MM").accountBalance();
        double eventBefore = engine.getEventState(1).currentEventAccountBalance();

        PurchaseResultDTO first = engine.purchaseShares("Buyer", 1, 1, 7);
        close(engine.getUser("Buyer").accountBalance(), buyerBefore - first.totalPricePaid(),
                "Buyer was not charged total payment");
        close(engine.getEventState(1).currentEventAccountBalance(), eventBefore + first.purchaseCost(),
                "Event did not receive purchase cost only");
        close(engine.getUser("MM").accountBalance(), mmBefore + first.commission(),
                "MM did not receive purchase commission");

        engine.purchaseShares("Other", 1, 2, 3);
        UserParticipationDTO buyer = participation(engine, "Buyer");
        UserParticipationDTO other = participation(engine, "Other");
        check(buyer.holdingsByOption().get("Yes") == 7, "Buyer holdings were not recorded");
        check(buyer.holdingsByOption().get("No") == 0, "Buyer holdings leaked from another user");
        check(other.holdingsByOption().get("No") == 3, "Other user holdings were not recorded");
        check(buyer.trades().size() == 1 && other.trades().size() == 1,
                "Per-user histories are not independent");
        close(buyer.totalCommissionPaid(), first.commission(), "Participation commission is wrong");
        expectFailure(() -> engine.purchaseShares("Buyer", 1, 1, 0), "greater than zero");
    }

    private static void negativeBalanceBlocksFurtherActions() throws Exception {
        GuessMarketEngine engine = engine(lmsrMarket("on-purchase", 0, 100, 500, 1, true));
        engine.startEvent("MM", 1);
        engine.purchaseShares("Buyer", 1, 1, 20);
        check(engine.getUser("Buyer").accountBalance() < 0.0, "Purchase should allow a negative balance");
        check(engine.getUser("Buyer").blocked(), "Negative buyer was not blocked");
        expectFailure(() -> engine.purchaseShares("Buyer", 1, 1, 1), "is blocked");
        expectFailure(() -> engine.startEvent("Buyer", 2), "is blocked");

        GuessMarketEngine blockedMm = engine(lmsrMarket("on-purchase", 0, 100, 500, 1, false));
        blockedMm.startEvent("MM", 1);
        blockedMm.purchaseShares("MM", 1, 1, 1000);
        check(blockedMm.getUser("MM").blocked(), "MM should be blocked after overspending");
        expectFailure(() -> blockedMm.closeEvent("MM", 1, 1), "is blocked");
    }

    private static void closeAuthorizationAndOnPurchaseSettlement() throws Exception {
        GuessMarketEngine engine = engine(lmsrMarket("on-purchase", 5, 100, 500, 100, false));
        expectFailure(() -> engine.closeEvent("MM", 1, 1), "Event must be active");
        engine.startEvent("MM", 1);
        engine.purchaseShares("Buyer", 1, 1, 8);
        expectFailure(() -> engine.closeEvent("Buyer", 1, 1), "Only the assigned Market Maker");

        double buyerBefore = engine.getUser("Buyer").accountBalance();
        engine.closeEvent("MM", 1, 1);
        close(engine.getUser("Buyer").accountBalance(), buyerBefore + 8.0,
                "ON_PURCHASE winner did not receive $1 per share");
        close(engine.getEventState(1).currentEventAccountBalance(), 0.0,
                "Settlement did not exhaust event account");
        expectFailure(() -> engine.purchaseShares("Buyer", 1, 1, 1), "Event must be active");
        expectFailure(() -> engine.startEvent("MM", 1), "only start from NOT_STARTED");
        expectFailure(() -> engine.closeEvent("MM", 1, 1), "Event must be active");
    }

    private static void onCloseSettlementAndBlockedPassiveCredit() throws Exception {
        GuessMarketEngine engine = engine(lmsrMarket("on-close", 25, 100, 500, 1, false));
        engine.startEvent("MM", 1);
        engine.purchaseShares("Buyer", 1, 1, 20);
        check(engine.getUser("Buyer").blocked(), "Winner should be blocked before passive payout");
        double buyerBefore = engine.getUser("Buyer").accountBalance();
        double mmBefore = engine.getUser("MM").accountBalance();
        double eventBefore = engine.getEventState(1).currentEventAccountBalance();

        engine.closeEvent("MM", 1, 1);
        close(engine.getUser("Buyer").accountBalance(), buyerBefore + 15.0,
                "ON_CLOSE net payout is wrong");
        close(engine.getUser("MM").accountBalance(), mmBefore + 5.0 + eventBefore - 20.0,
                "MM did not receive close commission and remaining subsidy");
        close(engine.getEventState(1).totalCommissionCollected(), 5.0,
                "Close commission total is wrong");
        close(participation(engine, "Buyer").totalCommissionPaid(), 5.0,
                "Participant close commission total is wrong");
        close(engine.getEventState(1).currentEventAccountBalance(), 0.0,
                "ON_CLOSE settlement did not exhaust event account");
    }

    private static UserParticipationDTO participation(GuessMarketEngine engine, String username) {
        return engine.getUser(username).participations().stream()
                .filter(item -> item.eventId() == 1).findFirst()
                .orElseThrow(() -> new AssertionError("Missing participation for " + username));
    }

    private static GuessMarketEngine engine(String xml) throws Exception {
        Path file = Files.createTempFile("guessmarket-lifecycle-", ".xml");
        Files.writeString(file, xml);
        file.toFile().deleteOnExit();
        GuessMarketEngine engine = new GuessMarketEngine();
        engine.loadMarketFromXml(file.toString());
        return engine;
    }

    private static String lmsrMarket(
            String commissionType, int commission, int b, int mmCash, int buyerCash, boolean secondEvent) {
        String extraEvent = secondEvent ? event(2, "Second", commissionType, commission, b) : "";
        String extraAssignment = secondEvent ? "<event id=\"2\"/>" : "";
        return "<Guess-Market><GM-events>" + event(1, "First", commissionType, commission, b) + extraEvent +
                "</GM-events><GM-users>" +
                "<GM-user name=\"MM\"><initial-cash>" + mmCash + "</initial-cash>" +
                "<GM-market-maker><event id=\"1\"/></GM-market-maker></GM-user>" +
                "<GM-user name=\"Buyer\"><initial-cash>" + buyerCash + "</initial-cash>" +
                (secondEvent ? "<GM-market-maker>" + extraAssignment + "</GM-market-maker>" : "") +
                "</GM-user><GM-user name=\"Other\"><initial-cash>200</initial-cash></GM-user>" +
                "</GM-users></Guess-Market>";
    }

    private static String event(int id, String name, String commissionType, int commission, int b) {
        return "<GM-event name=\"" + name + "\"><id>" + id + "</id><description>Test</description>" +
                "<commission type=\"" + commissionType + "\">" + commission + "</commission>" +
                "<GM-options><GM-option>Yes</GM-option><GM-option>No</GM-option></GM-options>" +
                "<GM-method><GM-LMSR><b>" + b + "</b></GM-LMSR></GM-method></GM-event>";
    }

    private static String orderBookMarket() {
        return "<Guess-Market><GM-events><GM-event name=\"OB\"><id>1</id><description>Test</description>" +
                "<commission type=\"on-purchase\">5</commission>" +
                "<GM-options><GM-option>Yes</GM-option><GM-option>No</GM-option></GM-options>" +
                "<GM-method><GM-order-book allow-mint=\"true\" initial=\"100\" d=\"1\"/></GM-method>" +
                "</GM-event></GM-events><GM-users><GM-user name=\"MM\"><initial-cash>500</initial-cash>" +
                "<GM-market-maker><event id=\"1\"/></GM-market-maker></GM-user></GM-users></Guess-Market>";
    }

    private static void expectFailure(ThrowingRunnable action, String text) {
        try {
            action.run();
            throw new AssertionError("Expected failure containing: " + text);
        } catch (RuntimeException expected) {
            check(expected.getMessage() != null && expected.getMessage().contains(text),
                    "Unexpected error: " + expected.getMessage());
        } catch (Exception unexpected) {
            throw new AssertionError(unexpected);
        }
    }

    private static void close(double actual, double expected, String message) {
        check(Math.abs(actual - expected) <= EPSILON, message + ": expected " + expected + ", got " + actual);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    @FunctionalInterface
    private interface ThrowingRunnable { void run() throws Exception; }
}
