package guessmarket.server;

import guessmarket.dto.EventDTO;
import guessmarket.dto.UserDTO;
import guessmarket.server.api.ApiException;
import guessmarket.server.api.EventApiService;
import guessmarket.server.api.EventUploadService;
import guessmarket.server.api.LoginResponse;
import guessmarket.server.api.UploadResponse;
import guessmarket.server.api.UserApiService;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public final class Ex03XmlUploadTest {
    public static void main(String[] args) throws Exception {
        schemaCompliantVariantsLoad();
        schemaInvalidDocumentsAreRejectedAtomically();
        validMultiEventUploadAndMarketMaker();
        cumulativeUploadsPreserveExistingEvents();
        duplicateAndInvalidUploadsAreAtomic();
        invalidSessionCannotUpload();
        concurrentDuplicateUploadHasOneWinner();
        System.out.println("Ex03XmlUploadTest: all checks passed");
    }

    private static void schemaCompliantVariantsLoad() {
        ServerState state = new ServerState();
        String token = new UserApiService(state).login("Schema User").sessionToken();
        UploadResponse minimal = upload(state, token, market(lmsr("Minimal", 1, "Yes", "No")));
        check(minimal.eventsAdded() == 1, "Minimal EX03 document did not load");
        upload(state, token, market(lmsr("LMSR", 25, "Up", "Down")));
        upload(state, token, market(orderBook("Order Book", 10, 100, false, "A", "B")));
        upload(state, token, market(lmsr("Two Options", 5, "First", "Second")));
        check(new EventApiService(state).summaries().size() == 4,
                "Schema-compliant LMSR, Order Book, or multi-option XML failed");
    }

    private static void schemaInvalidDocumentsAreRejectedAtomically() {
        ServerState state = new ServerState();
        String token = new UserApiService(state).login("Schema User").sessionToken();
        upload(state, token, market(lmsr("Baseline", 10, "A", "B")));
        int baseline = new EventApiService(state).summaries().size();

        expectInvalidXml(state, token, market(
                "<GM-event name=\"Missing Description\">"
                        + "<commission type=\"on-close\">5</commission>"
                        + options("A", "B")
                        + "<GM-method><GM-LMSR><b>10</b></GM-LMSR></GM-method></GM-event>"));
        expectInvalidXml(state, token, market(
                "<GM-event name=\"Bad Attribute\"><description>Bad</description>"
                        + "<commission type=\"sometimes\">5</commission>"
                        + options("A", "B")
                        + "<GM-method><GM-LMSR><b>10</b></GM-LMSR></GM-method></GM-event>"));
        expectInvalidXml(state, token, market(
                "<GM-event name=\"Wrong Order\"><commission type=\"on-close\">5</commission>"
                        + "<description>Bad</description>" + options("A", "B")
                        + "<GM-method><GM-LMSR><b>10</b></GM-LMSR></GM-method></GM-event>"));
        expectInvalidXml(state, token,
                market(lmsr("Old Users", 10, "A", "B"))
                        .replace("</Guess-Market>", "<GM-users/></Guess-Market>"));
        expectInvalidXml(state, token, market(
                "<GM-event name=\"Old Id\"><id>7</id><description>Bad</description>"
                        + "<commission type=\"on-close\">5</commission>" + options("A", "B")
                        + "<GM-method><GM-LMSR><b>10</b></GM-LMSR></GM-method></GM-event>"));
        expectInvalidXml(state, token, "<Guess-Market><GM-events>");
        expectInvalidXml(state, token, market(lmsr("Too Many", 10, "A", "B", "C")));
        expectInvalidXml(state, token, market(
                lmsr("Would Be Partial", 10, "A", "B"),
                "<GM-event name=\"Broken Second\"><description>Bad</description>"
                        + "<commission type=\"on-close\">5</commission>" + options("A", "B")
                        + "<GM-method><GM-order-book initial=\"100\" d=\"10\"/>"
                        + "</GM-method></GM-event>"));

        check(new EventApiService(state).summaries().size() == baseline,
                "Schema-invalid upload partially modified event state");
        check(new UserApiService(state).currentUser(token).marketMakerEventIds().size() == baseline,
                "Schema-invalid upload partially modified MM state");
    }

    private static void validMultiEventUploadAndMarketMaker() {
        ServerState state = new ServerState();
        LoginResponse login = new UserApiService(state).login("Uploader");
        UploadResponse response = upload(state, login.sessionToken(), market(
                lmsr("Weather", 5, "Sunny", "Rainy"),
                orderBook("Election", 10, 100, true, "Yes", "No")));
        check(response.eventsAdded() == 2, "Valid upload did not add every event");
        List<EventDTO> events = new EventApiService(state).summaries();
        check(events.size() == 2 && events.get(0).options().equals(
                List.of("Sunny", "Rainy")), "Option order was not preserved");
        check(events.stream().allMatch(event -> event.marketMakerUsername().equals("Uploader")
                && event.eventState().equals("NOT_STARTED") && event.currentEventAccountBalance() == 0.0),
                "Uploader/MM or initial lifecycle state is wrong");
        UserDTO uploader = new UserApiService(state).currentUser(login.sessionToken());
        check(uploader.marketMaker() && uploader.marketMakerEventIds().size() == 2,
                "Uploader did not receive all MM assignments");
    }

    private static void cumulativeUploadsPreserveExistingEvents() {
        ServerState state = new ServerState();
        String token = new UserApiService(state).login("Uploader").sessionToken();
        upload(state, token, market(lmsr("First", 10, "A", "B")));
        upload(state, token, market(lmsr("Second", 10, "X", "Y")));
        check(new EventApiService(state).summaries().stream().map(EventDTO::eventName).toList()
                        .equals(List.of("First", "Second")),
                "Later upload replaced or reordered existing events");
    }

    private static void duplicateAndInvalidUploadsAreAtomic() {
        ServerState state = new ServerState();
        String token = new UserApiService(state).login("Uploader").sessionToken();
        upload(state, token, market(lmsr("Existing", 10, "A", "B")));
        int baseline = new EventApiService(state).summaries().size();
        expectFailure(() -> upload(state, token, market(
                lmsr("Dup", 10, "A", "B"), lmsr(" dUP ", 10, "C", "D"))),
                400, "INVALID_XML");
        expectFailure(() -> upload(state, token, market(
                lmsr("New", 10, "A", "B"), lmsr(" existing ", 10, "C", "D"))),
                409, "DUPLICATE_EVENT_NAME");
        expectFailure(() -> upload(state, token, market(
                "<GM-event name=\"Bad\"><description>Bad</description>"
                        + "<commission type=\"on-purchase\">100</commission>"
                        + "<GM-options><GM-option>A</GM-option><GM-option>B</GM-option></GM-options>"
                        + "<GM-method><GM-LMSR><b>10</b></GM-LMSR></GM-method></GM-event>")),
                400, "INVALID_XML");
        expectFailure(() -> upload(state, token, "<Guess-Market><broken/></Guess-Market>"),
                400, "INVALID_XML");
        expectFailure(() -> upload(state, token, market(lmsr("Bad B", 0, "A", "B"))),
                400, "INVALID_XML");
        expectFailure(() -> upload(state, token, market(lmsr("Bad Options", 10, "Same", " same "))),
                400, "INVALID_XML");
        expectFailure(() -> upload(state, token, market(
                orderBook("Bad Mint", 10, 100, true, "A", "B", "C"))),
                400, "INVALID_XML");
        check(new EventApiService(state).summaries().size() == baseline,
                "Failed upload partially mutated events");
        check(new UserApiService(state).currentUser(token).marketMakerEventIds().size() == baseline,
                "Failed upload partially mutated MM assignments");
    }

    private static void invalidSessionCannotUpload() {
        ServerState state = new ServerState();
        expectFailure(() -> upload(state, "invalid", market(lmsr("Nope", 10, "A", "B"))),
                401, "INVALID_SESSION");
        check(new EventApiService(state).summaries().isEmpty(), "Invalid session mutated market");
    }

    private static void concurrentDuplicateUploadHasOneWinner() throws Exception {
        ServerState state = new ServerState();
        UserApiService users = new UserApiService(state);
        String first = users.login("First").sessionToken();
        String second = users.login("Second").sessionToken();
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger duplicates = new AtomicInteger();
        Runnable attemptOne = () -> concurrentUpload(state, first, start, successes, duplicates);
        Runnable attemptTwo = () -> concurrentUpload(state, second, start, successes, duplicates);
        Thread a = Thread.ofPlatform().start(attemptOne);
        Thread b = Thread.ofPlatform().start(attemptTwo);
        start.countDown();
        a.join();
        b.join();
        check(successes.get() == 1 && duplicates.get() == 1,
                "Concurrent duplicate uploads did not produce one winner");
        check(new EventApiService(state).summaries().size() == 1,
                "Concurrent duplicate upload created multiple events");
    }

    private static void concurrentUpload(
            ServerState state, String token, CountDownLatch start,
            AtomicInteger successes, AtomicInteger duplicates) {
        try {
            start.await();
            upload(state, token, market(lmsr("Shared", 10, "A", "B")));
            successes.incrementAndGet();
        } catch (ApiException error) {
            if (error.code().equals("DUPLICATE_EVENT_NAME")) duplicates.incrementAndGet();
            else throw error;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError(error);
        }
    }

    private static UploadResponse upload(ServerState state, String token, String xml) {
        return new EventUploadService(state).upload(token,
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private static String market(String... events) {
        return "<Guess-Market><GM-events>" + String.join("", events)
                + "</GM-events></Guess-Market>";
    }

    private static String lmsr(String name, int b, String... options) {
        return eventStart(name) + options(options)
                + "<GM-method><GM-LMSR><b>" + b + "</b></GM-LMSR></GM-method></GM-event>";
    }

    private static String orderBook(
            String name, int d, int initial, boolean mint, String... options) {
        return eventStart(name) + options(options) + "<GM-method><GM-order-book d=\"" + d
                + "\" initial=\"" + initial + "\" allow-mint=\"" + mint
                + "\"/></GM-method></GM-event>";
    }

    private static String eventStart(String name) {
        return "<GM-event name=\"" + name + "\"><description>Test event</description>"
                + "<commission type=\"on-purchase\">5</commission>";
    }

    private static String options(String... options) {
        StringBuilder xml = new StringBuilder("<GM-options>");
        for (String option : options) xml.append("<GM-option>").append(option).append("</GM-option>");
        return xml.append("</GM-options>").toString();
    }

    private static void expectFailure(Runnable action, int status, String code) {
        try {
            action.run();
            throw new AssertionError("Expected failure: " + code);
        } catch (ApiException expected) {
            check(expected.status() == status && expected.code().equals(code),
                    "Unexpected API failure: " + expected.code() + " / " + expected.getMessage());
        }
    }

    private static void expectInvalidXml(ServerState state, String token, String xml) {
        expectFailure(() -> upload(state, token, xml), 400, "INVALID_XML");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
