package guessmarket.service;

import guessmarket.domain.CommissionMethod;
import guessmarket.domain.OrderSide;
import guessmarket.dto.CreateEventRequest;
import guessmarket.dto.EventStateDTO;
import guessmarket.dto.UserDTO;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class CreateEventBonusTest {
    public static void main(String[] args) throws Exception {
        successfulCreationAndQueries();
        invalidRequestsAreAtomic();
        unknownAndBlockedCreatorsAreRejected();
        createdLmsrCompletesLifecycle();
        createdOrderBookCompletesLifecycle();
        System.out.println("CreateEventBonusTest: all checks passed");
    }

    private static void successfulCreationAndQueries() throws Exception {
        GuessMarketEngine engine = engine(1_000, 1_000);
        EventStateDTO lmsr = engine.createEvent(lmsr("Creator", "Created LMSR", 10));
        EventStateDTO orderBook = engine.createEvent(orderBook("Creator", "Created OB", 10, 100));

        check(lmsr.id() == 1 && orderBook.id() == 2, "IDs are not unique positive gaps");
        check(lmsr.eventState().equals("NOT_STARTED") && orderBook.eventState().equals("NOT_STARTED"),
                "Created event did not start in NOT_STARTED");
        check(lmsr.marketMakerUsername().equals("Creator"), "Creator was not assigned as LMSR MM");
        check(orderBook.marketMakerUsername().equals("Creator"), "Creator was not assigned as OB MM");
        check(engine.getEventSummaries().stream().anyMatch(event -> event.id() == lmsr.id()),
                "Created event is absent from event summaries");
        UserDTO creator = engine.getUser("creator");
        check(creator.marketMakerEventIds().containsAll(List.of(1, 2)),
                "Created events are absent from the creator's MM query");
        check(engine.getEventState(10).eventState().equals("NOT_STARTED"),
                "Creation changed an existing event");
    }

    private static void invalidRequestsAreAtomic() throws Exception {
        GuessMarketEngine engine = engine(1_000, 1_000);
        expectAtomicFailure(engine, new CreateEventRequest("Creator", "Duplicate options", "Test",
                List.of("Yes", " yes "), CommissionMethod.ON_PURCHASE, 5,
                new CreateEventRequest.LmsrConfiguration(10)), "distinct");
        expectAtomicFailure(engine, lmsr("Creator", "Invalid b", 0), "greater than zero");
        expectAtomicFailure(engine, orderBook("Creator", "Invalid d", 0, 100), "greater than zero");
        expectAtomicFailure(engine, orderBook("Creator", "Invalid initial", 10, -1), "cannot be negative");
        expectAtomicFailure(engine, orderBook("Creator", "Indivisible initial", 10, 95), "divisible by d");
        expectAtomicFailure(engine, new CreateEventRequest("Creator", "Invalid commission", "Test",
                List.of("Yes", "No"), CommissionMethod.ON_CLOSE, 91,
                new CreateEventRequest.LmsrConfiguration(10)), "between 0 and 90");
        expectAtomicFailure(engine, new CreateEventRequest("Creator", "Missing commission", "Test",
                List.of("Yes", "No"), null, 5,
                new CreateEventRequest.LmsrConfiguration(10)), "cannot be null");
    }

    private static void unknownAndBlockedCreatorsAreRejected() throws Exception {
        GuessMarketEngine engine = engine(1_000, 1);
        expectAtomicFailure(engine, lmsr("Missing", "Unknown creator", 10), "No user exists");

        engine.startEvent("Creator", 10);
        engine.purchaseShares("Buyer", 10, 1, 20);
        check(engine.getUser("Buyer").blocked(), "Test setup did not block Buyer");
        expectAtomicFailure(engine, lmsr("Buyer", "Blocked creator", 10), "is blocked");
    }

    private static void createdLmsrCompletesLifecycle() throws Exception {
        GuessMarketEngine engine = engine(1_000, 1_000);
        int id = engine.createEvent(lmsr("Creator", "Lifecycle LMSR", 10)).id();
        engine.startEvent("Creator", id);
        engine.purchaseShares("Buyer", id, 1, 5);
        engine.closeEvent("Creator", id, 1);
        check(engine.getEventState(id).eventState().equals("CLOSED"),
                "Created LMSR did not complete its lifecycle");
    }

    private static void createdOrderBookCompletesLifecycle() throws Exception {
        GuessMarketEngine engine = engine(1_000, 1_000);
        int id = engine.createEvent(orderBook("Creator", "Lifecycle OB", 10, 100)).id();
        engine.startEvent("Creator", id);
        engine.submitOrder("Creator", id, 1, OrderSide.SELL, 2, 4.0);
        engine.submitOrder("Buyer", id, 1, OrderSide.BUY, 2, 4.0);
        engine.closeEvent("Creator", id, 1);
        check(engine.getEventState(id).eventState().equals("CLOSED"),
                "Created Order Book did not complete its lifecycle");
    }

    private static CreateEventRequest lmsr(String creator, String name, int b) {
        return new CreateEventRequest(creator, name, "Created by regression test",
                List.of("Yes", "No"), CommissionMethod.ON_PURCHASE, 5,
                new CreateEventRequest.LmsrConfiguration(b));
    }

    private static CreateEventRequest orderBook(String creator, String name, int d, int initial) {
        return new CreateEventRequest(creator, name, "Created by regression test",
                List.of("Up", "Down"), CommissionMethod.ON_CLOSE, 5,
                new CreateEventRequest.OrderBookConfiguration(d, initial, true));
    }

    private static void expectAtomicFailure(
            GuessMarketEngine engine, CreateEventRequest request, String expectedMessage) {
        int eventCount = engine.getEventSummaries().size();
        List<Integer> assignments = engine.getUser("Creator").marketMakerEventIds();
        try {
            engine.createEvent(request);
            throw new AssertionError("Expected failure containing: " + expectedMessage);
        } catch (RuntimeException expected) {
            check(expected.getMessage() != null && expected.getMessage().contains(expectedMessage),
                    "Unexpected error: " + expected.getMessage());
        }
        check(engine.getEventSummaries().size() == eventCount, "Failed creation changed event count");
        check(engine.getUser("Creator").marketMakerEventIds().equals(assignments),
                "Failed creation changed MM assignments");
    }

    private static GuessMarketEngine engine(int creatorCash, int buyerCash) throws Exception {
        String xml = "<Guess-Market><GM-events><GM-event name=\"Existing\"><id>10</id>" +
                "<description>Existing event</description><commission type=\"on-purchase\">0</commission>" +
                "<GM-options><GM-option>Yes</GM-option><GM-option>No</GM-option></GM-options>" +
                "<GM-method><GM-LMSR><b>1</b></GM-LMSR></GM-method></GM-event></GM-events>" +
                "<GM-users><GM-user name=\"Creator\"><initial-cash>" + creatorCash + "</initial-cash>" +
                "<GM-market-maker><event id=\"10\"/></GM-market-maker></GM-user>" +
                "<GM-user name=\"Buyer\"><initial-cash>" + buyerCash + "</initial-cash></GM-user>" +
                "</GM-users></Guess-Market>";
        Path file = Files.createTempFile("guessmarket-create-event-", ".xml");
        Files.writeString(file, xml);
        file.toFile().deleteOnExit();
        GuessMarketEngine engine = new GuessMarketEngine();
        engine.loadMarketFromXml(file.toString());
        return engine;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
