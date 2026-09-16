package guessmarket.service;

import guessmarket.domain.OrderSide;
import guessmarket.dto.*;

import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DtoQueryRegressionTest {
    private static final double EPSILON = 1.0e-8;

    public static void main(String[] args) throws Exception {
        lmsrQueries();
        orderBookQueries();
        System.out.println("DtoQueryRegressionTest: all checks passed");
    }

    private static void lmsrQueries() throws Exception {
        GuessMarketEngine engine = engine(lmsrMarket());
        EventDTO summary = engine.getEventSummaries().getFirst();
        check(summary.tradingMethod().equals("LMSR"), "LMSR method missing from summary");
        check(summary.marketMakerUsername().equals("MM"), "MM missing from summary");
        close(summary.currentEventAccountBalance(), 0, "Initial summary balance is wrong");
        immutable(() -> engine.getEventSummaries().clear());
        immutable(() -> summary.options().add("Maybe"));

        engine.startEvent("MM", 1);
        engine.purchaseShares("Buyer", 1, 1, 2);
        engine.purchaseShares("Buyer", 1, 2, 3);
        EventStateDTO state = engine.getEventState(1);
        check(state.lmsrDetails() != null && state.orderBookDetails() == null, "Wrong LMSR detail shape");
        check(state.lmsrDetails().liquidityParameter() == 10, "LMSR liquidity parameter is missing");
        check(state.optionStateDTOList().size() == 2, "Legacy LMSR option state was not preserved");
        check(state.trades().getFirst().boughtOptionName().equals("No"), "Global trades are not newest first");
        UserParticipationDTO participation = participation(engine, "Buyer");
        check(participation.eventName().equals("LMSR") && participation.tradingMethod().equals("LMSR")
                && participation.eventState().equals("ACTIVE"), "Participation event context is incomplete");
        check(participation.trades().getFirst().boughtOptionName().equals("No"),
                "Personal trades are not newest first");
        check(participation.profitLoss() == null, "Active participation has fake final P/L");
        immutable(() -> participation.holdingsByOption().put("Yes", 99));
        immutable(() -> state.participants().clear());
    }

    private static void orderBookQueries() throws Exception {
        GuessMarketEngine engine = engine(orderBookMarket());
        engine.startEvent("MM", 1);
        engine.submitOrder("Buyer", 1, 1, OrderSide.BUY, 3, 4.0);
        engine.submitOrder("MM", 1, 1, OrderSide.SELL, 2, 6.0);
        EventStateDTO open = engine.getEventState(1);
        OrderBookDetailsDTO details = open.orderBookDetails();
        check(details != null && open.lmsrDetails() == null && details.optionBooks().size() == 2,
                "Order Book option books missing");
        check(details.d() == 10 && !details.allowMint() && details.initial() == 40,
                "Order Book configuration is wrong");
        OrderBookOptionDTO yes = details.optionBooks().getFirst();
        PendingOrderDTO buy = yes.pendingBuyOrders().getFirst();
        check(buy.username().equals("Buyer") && buy.side().equals("BUY")
                        && buy.remainingQuantity() == 3 && buy.pricePerShare() == 4.0,
                "Pending BUY projection is wrong");
        check(yes.pendingSellOrders().getFirst().username().equals("MM"), "Pending SELL projection is wrong");
        check(yes.last() == null && yes.bid() == 4.0 && yes.ask() == 6.0
                        && yes.mid() == 5.0 && yes.spread() == 2.0,
                "Open-book statistics are wrong");
        check(details.optionBooks().get(1).last() == null && details.optionBooks().get(1).mid() == null,
                "Missing statistics were fabricated");
        for (RecordComponent component : PendingOrderDTO.class.getRecordComponents()) {
            check(!component.getType().getName().equals("guessmarket.domain.User"),
                    "Pending order DTO exposes User");
        }
        check(open.participants().stream().anyMatch(item -> item.username().equals("Buyer")),
                "Resting-order-only participant missing");
        check(open.participants().stream().anyMatch(item -> item.username().equals("MM")
                        && item.holdingsByOption().get("Yes") > 0), "Holder participant missing");
        UserParticipationDTO buyer = participation(engine, "Buyer");
        check(buyer.holdingsByOption().get("Yes") == 0 && buyer.profitLoss() == null,
                "Active OB participation is wrong");
        check(buyer.currentHoldingValueByOption().get("No") == null,
                "Unavailable holding value was fabricated");
        immutable(() -> yes.pendingBuyOrders().clear());

        engine.submitOrder("MM", 1, 1, OrderSide.SELL, 2, 4.0);
        OrderBookOptionDTO tradedBook = engine.getEventState(1).orderBookDetails().optionBooks().getFirst();
        close(tradedBook.last(), 4.0, "LAST is wrong after execution");
        close(tradedBook.bid(), 4.0, "BID is wrong after execution");
        close(tradedBook.ask(), 6.0, "ASK is wrong after execution");
        close(tradedBook.mid(), 5.0, "MID is wrong after execution");
        close(tradedBook.spread(), 2.0, "SPREAD is wrong after execution");
        UserParticipationDTO filled = participation(engine, "Buyer");
        close(filled.totalCashPaid(), 8.8, "OB cash paid is wrong");
        close(filled.totalCommissionPaid(), 0.8, "OB commission paid is wrong");
        close(filled.cumulativePurchaseAmountByOption().get("Yes"), 8.0,
                "OB cumulative gross purchase amount is wrong");
        close(participation(engine, "MM").totalCashReceived(), 8.0, "OB seller cash received is wrong");
        engine.closeEvent("MM", 1, 1);
        UserParticipationDTO closed = participation(engine, "Buyer");
        close(closed.totalCashReceived(), 20.0, "OB settlement cash received is wrong");
        close(closed.profitLoss(), 11.2, "Closed OB final P/L is wrong");
        check(closed.winningOption().equals("Yes") && closed.eventState().equals("CLOSED"),
                "Closed participation context is wrong");
    }

    private static UserParticipationDTO participation(GuessMarketEngine engine, String username) {
        return engine.getUser(username).participations().stream().filter(item -> item.eventId() == 1)
                .findFirst().orElseThrow();
    }

    private static GuessMarketEngine engine(String xml) throws Exception {
        Path file = Files.createTempFile("guessmarket-dto-query-", ".xml");
        Files.writeString(file, xml);
        file.toFile().deleteOnExit();
        GuessMarketEngine engine = new GuessMarketEngine();
        engine.loadMarketFromXml(file.toString());
        return engine;
    }

    private static String lmsrMarket() {
        return market("LMSR", "<GM-LMSR><b>10</b></GM-LMSR>", 0);
    }

    private static String orderBookMarket() {
        return market("OB", "<GM-order-book allow-mint=\"false\" initial=\"40\" d=\"10\"/>", 10);
    }

    private static String market(String name, String method, int commission) {
        return "<Guess-Market><GM-events><GM-event name=\"" + name + "\"><id>1</id>" +
                "<description>Test</description><commission type=\"on-purchase\">" + commission +
                "</commission><GM-options><GM-option>Yes</GM-option><GM-option>No</GM-option></GM-options>" +
                "<GM-method>" + method + "</GM-method></GM-event></GM-events><GM-users>" +
                user("MM", 100, true) + user("Buyer", 100, false) + "</GM-users></Guess-Market>";
    }

    private static String user(String name, int cash, boolean mm) {
        return "<GM-user name=\"" + name + "\"><initial-cash>" + cash + "</initial-cash>" +
                (mm ? "<GM-market-maker><event id=\"1\"/></GM-market-maker>" : "") + "</GM-user>";
    }

    private static void immutable(Runnable mutation) {
        try {
            mutation.run();
            throw new AssertionError("Collection is mutable");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }

    private static void close(double actual, double expected, String message) {
        check(Math.abs(actual - expected) <= EPSILON, message + ": expected " + expected + ", got " + actual);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
