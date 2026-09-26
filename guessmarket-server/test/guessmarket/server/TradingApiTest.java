package guessmarket.server;

import guessmarket.dto.EventStateDTO;
import guessmarket.dto.OrderSubmissionResultDTO;
import guessmarket.dto.PurchaseResultDTO;
import guessmarket.server.api.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

public final class TradingApiTest {
    public static void main(String[] args) {
        lifecycleAndLmsrSettlement();
        orderBookMatchingAndSettlement();
        validationAndAuthorization();
        System.out.println("TradingApiTest: all checks passed");
    }

    private static void lifecycleAndLmsrSettlement() {
        Fixture f = fixture();
        f.upload(lmsr("LMSR Event", "on-purchase", 10));
        expect(() -> f.api.start(f.bob, 1), 400, "INVALID_OPERATION");
        expect(() -> f.api.start(f.alice, 1), 409, "OPERATION_REJECTED");
        f.users.topUp(f.alice, 10_000);
        f.users.topUp(f.bob, 10_000);
        f.api.start(f.alice, 1);
        String broke = f.users.login("Broke").sessionToken();
        f.api.purchase(broke, 1, new PurchaseRequest(1, 1));
        check(f.users.currentUser(broke).blocked(),
                "Insufficient LMSR balance did not complete then block the user per EX03 rules");
        expect(() -> f.api.purchase(broke, 1, new PurchaseRequest(1, 1)),
                409, "OPERATION_REJECTED");
        PurchaseResultDTO purchase = f.api.purchase(f.bob, 1, new PurchaseRequest(1, 4));
        check(purchase.purchaseCost() > 0 && purchase.commission() > 0
                && purchase.totalPricePaid() == purchase.purchaseCost() + purchase.commission(),
                "LMSR purchase/commission response is wrong");
        expect(() -> f.api.purchase(f.bob, 1, new PurchaseRequest(3, 1)), 400, "INVALID_OPERATION");
        expect(() -> f.api.purchase(f.bob, 1, new PurchaseRequest(1, 0)), 400, "INVALID_QUANTITY");
        expect(() -> f.api.close(f.bob, 1, new CloseEventRequest(1)), 400, "INVALID_OPERATION");
        expect(() -> f.api.close(f.alice, 1, new CloseEventRequest(3)), 400, "INVALID_OPERATION");
        EventStateDTO closed = f.api.close(f.alice, 1, new CloseEventRequest(1));
        check(closed.eventState().equals("CLOSED") && closed.winningOption().equals("Yes"),
                "LMSR close failed");
        expect(() -> f.api.purchase(f.bob, 1, new PurchaseRequest(1, 1)), 409, "OPERATION_REJECTED");
        check(f.users.currentUser(f.bob).accountTransactions().stream()
                        .anyMatch(tx -> tx.type().equals("LMSR_PURCHASE"))
                && f.users.currentUser(f.bob).accountTransactions().stream()
                        .anyMatch(tx -> tx.type().equals("EVENT_SETTLEMENT")),
                "LMSR transaction history omitted purchase or settlement");
    }

    private static void orderBookMatchingAndSettlement() {
        Fixture f = fixture();
        f.upload(orderBook("OB Event", "on-close", 10, 100, true));
        f.users.topUp(f.alice, 10_000);
        f.users.topUp(f.bob, 10_000);
        String carol = f.users.login("Carol").sessionToken();
        f.users.topUp(carol, 10_000);
        f.api.start(f.alice, 1);

        OrderSubmissionResultDTO pending = f.api.order(f.bob, 1,
                new OrderRequest("BUY", 1, 7, 4));
        check(pending.remainingQuantity() == 7, "BUY did not rest");
        OrderSubmissionResultDTO partial = f.api.order(f.alice, 1,
                new OrderRequest("SELL", 1, 3, 4));
        check(partial.executions().size() == 1 && pending.originalQuantity() == 7,
                "Partial cross failed");
        OrderSubmissionResultDTO multiple = f.api.order(f.alice, 1,
                new OrderRequest("SELL", 1, 4, 4));
        check(multiple.executions().size() == 1 && multiple.remainingQuantity() == 0,
                "Remaining BUY fill failed");
        f.api.order(f.alice, 1, new OrderRequest("SELL", 1, 1, 5));
        f.api.order(f.alice, 1, new OrderRequest("SELL", 1, 1, 5));
        OrderSubmissionResultDTO multiFill = f.api.order(f.bob, 1,
                new OrderRequest("BUY", 1, 2, 5));
        check(multiFill.executions().size() == 2,
                "One incoming order did not fill multiple resting orders");
        f.api.order(f.bob, 1, new OrderRequest("BUY", 1, 2, 6));
        OrderSubmissionResultDTO mint = f.api.order(carol, 1,
                new OrderRequest("BUY", 2, 2, 4));
        check(!mint.mintExecutions().isEmpty(), "Binary MINT did not execute");
        expect(() -> f.api.order(f.bob, 1, new OrderRequest("SELL", 2, 999, 3)),
                400, "INVALID_OPERATION");
        expect(() -> f.api.order(f.bob, 1, new OrderRequest("BUY", 1, 1, 10)),
                400, "INVALID_OPERATION");
        EventStateDTO closed = f.api.close(f.alice, 1, new CloseEventRequest(1));
        check(closed.eventState().equals("CLOSED") && closed.totalCommissionCollected() > 0,
                "Order Book close/on-close commission failed");
        var aliceTransactions = f.users.currentUser(f.alice).accountTransactions();
        check(aliceTransactions.stream()
                        .anyMatch(tx -> tx.type().equals("EVENT_SETTLEMENT")
                                && tx.description().contains("commissions")),
                "MM commission receipt is missing: " + aliceTransactions);
    }

    private static void validationAndAuthorization() {
        Fixture f = fixture();
        f.upload(lmsr("Locked", "on-purchase", 10));
        expect(() -> f.api.start("bad-session", 1), 401, "INVALID_SESSION");
        expect(() -> f.api.purchase(f.bob, 1, new PurchaseRequest(1, 1)),
                409, "OPERATION_REJECTED");
        expect(() -> f.api.order(f.bob, 1, new OrderRequest("NOPE", 1, 1, 1)),
                400, "INVALID_ORDER_SIDE");
    }

    private static Fixture fixture() {
        ServerState state = new ServerState();
        UserApiService users = new UserApiService(state);
        return new Fixture(state, users, new TradingApiService(state),
                users.login("Alice").sessionToken(), users.login("Bob").sessionToken());
    }

    private record Fixture(ServerState state, UserApiService users, TradingApiService api,
                           String alice, String bob) {
        void upload(String event) {
            new EventUploadService(state).upload(alice, new ByteArrayInputStream(
                    ("<Guess-Market><GM-events>" + event + "</GM-events></Guess-Market>")
                            .getBytes(StandardCharsets.UTF_8)));
        }
    }

    private static String lmsr(String name, String commission, int b) {
        return start(name, commission) + "<GM-method><GM-LMSR><b>" + b
                + "</b></GM-LMSR></GM-method></GM-event>";
    }

    private static String orderBook(String name, String commission, int d, int initial, boolean mint) {
        return start(name, commission) + "<GM-method><GM-order-book initial=\"" + initial
                + "\" d=\"" + d + "\" allow-mint=\"" + mint + "\"/></GM-method></GM-event>";
    }

    private static String start(String name, String commission) {
        return "<GM-event name=\"" + name + "\"><description>API test</description>"
                + "<commission type=\"" + commission + "\">5</commission>"
                + "<GM-options><GM-option>Yes</GM-option><GM-option>No</GM-option></GM-options>";
    }

    private static void expect(Runnable action, int status, String code) {
        try { action.run(); throw new AssertionError("Expected " + code); }
        catch (ApiException error) {
            check(error.status() == status && error.code().equals(code),
                    "Expected " + status + "/" + code + " but got " + error.status() + "/" + error.code());
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
