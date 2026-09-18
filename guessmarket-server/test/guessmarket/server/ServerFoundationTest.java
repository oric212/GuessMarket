package guessmarket.server;

import guessmarket.domain.CommissionMethod;
import guessmarket.dto.CreateEventRequest;
import guessmarket.dto.EventStateDTO;
import guessmarket.server.api.ApiError;
import guessmarket.server.api.ApiException;
import guessmarket.server.api.EventApiService;
import guessmarket.server.api.HealthServlet;
import guessmarket.server.api.JsonSupport;
import guessmarket.service.GuessMarketEngine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class ServerFoundationTest {
    public static void main(String[] args) throws Exception {
        sharedStateAndHealth();
        jsonRoundTripAndErrors();
        eventSerializationAndLookup();
        concurrentReadsShareOneEngine();
        concurrentWritesAreSerialized();
        System.out.println("ServerFoundationTest: all checks passed");
    }

    private static void sharedStateAndHealth() {
        ServerState state = new ServerState();
        check(state.engineIdentity() == state.engineIdentity(), "Server state does not retain one engine");
        HealthServlet.HealthResponse health = HealthServlet.response();
        check(health.success() && health.status().equals("UP"), "Health response is not healthy");
    }

    private static void jsonRoundTripAndErrors() {
        String json = JsonSupport.toJson(new ApiError("TEST_ERROR", "Readable message"));
        ApiError restored = JsonSupport.fromJson(json, ApiError.class);
        check(!restored.success() && restored.code().equals("TEST_ERROR")
                && restored.message().equals("Readable message"), "JSON round trip failed");
        expectApiFailure(() -> JsonSupport.fromJson("{broken", ApiError.class),
                400, "MALFORMED_JSON");
        expectApiFailure(() -> JsonSupport.fromJson(" ", ApiError.class),
                400, "MALFORMED_JSON");
    }

    private static void eventSerializationAndLookup() throws Exception {
        GuessMarketEngine engine = populatedEngine();
        ServerState state = new ServerState(engine);
        EventApiService events = new EventApiService(state);
        check(events.summaries().size() == 2, "Event summaries did not use the shared engine");
        EventStateDTO details = events.details(" Three-Way Forecast ");
        check(details.options().equals(List.of("Sunny", "Cloudy", "Rainy")),
                "Multi-option event detail serialization lost options");
        String json = JsonSupport.toJson(details);
        check(json.contains("\"eventName\":\"Three-Way Forecast\"")
                && json.contains("\"Rainy\""), "Event detail JSON is incomplete");
        expectApiFailure(() -> events.details("Missing"), 404, "EVENT_NOT_FOUND");
        expectApiFailure(() -> events.details(" "), 400, "MISSING_EVENT_NAME");
    }

    private static void concurrentReadsShareOneEngine() throws Exception {
        ServerState state = new ServerState(populatedEngine());
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread[] readers = new Thread[8];
        for (int index = 0; index < readers.length; index++) {
            readers[index] = Thread.ofPlatform().start(() -> {
                try {
                    start.await();
                    for (int iteration = 0; iteration < 100; iteration++) {
                        check(state.read(engine -> engine.getEventSummaries().size()) == 2,
                                "Concurrent read observed corrupted state");
                    }
                } catch (Throwable error) {
                    failure.compareAndSet(null, error);
                }
            });
        }
        start.countDown();
        for (Thread reader : readers) reader.join();
        if (failure.get() != null) throw new AssertionError("Concurrent state access failed", failure.get());
    }

    private static void concurrentWritesAreSerialized() throws Exception {
        ServerState state = new ServerState();
        AtomicInteger activeWriters = new AtomicInteger();
        AtomicInteger maximumWriters = new AtomicInteger();
        Thread[] writers = new Thread[8];
        for (int index = 0; index < writers.length; index++) {
            writers[index] = Thread.ofPlatform().start(() -> state.write(engine -> {
                int active = activeWriters.incrementAndGet();
                maximumWriters.accumulateAndGet(active, Math::max);
                try {
                    Thread.sleep(5);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(error);
                } finally {
                    activeWriters.decrementAndGet();
                }
                return null;
            }));
        }
        for (Thread writer : writers) writer.join();
        check(maximumWriters.get() == 1, "Concurrent mutations were not serialized");
    }

    private static GuessMarketEngine populatedEngine() throws Exception {
        String xml = "<Guess-Market><GM-events><GM-event name=\"Existing\"><id>10</id>"
                + "<description>Existing</description><commission type=\"on-purchase\">0</commission>"
                + "<GM-options><GM-option>Yes</GM-option><GM-option>No</GM-option></GM-options>"
                + "<GM-method><GM-LMSR><b>10</b></GM-LMSR></GM-method></GM-event></GM-events>"
                + "<GM-users><GM-user name=\"Creator\"><initial-cash>1000</initial-cash>"
                + "<GM-market-maker><event id=\"10\"/></GM-market-maker></GM-user></GM-users></Guess-Market>";
        Path file = Files.createTempFile("guessmarket-server-test-", ".xml");
        Files.writeString(file, xml);
        file.toFile().deleteOnExit();
        GuessMarketEngine engine = new GuessMarketEngine();
        engine.loadMarketFromXml(file.toString());
        engine.createEvent(new CreateEventRequest(
                "Creator", "Three-Way Forecast", "Weather outcome",
                List.of("Sunny", "Cloudy", "Rainy"), CommissionMethod.ON_PURCHASE, 0,
                new CreateEventRequest.LmsrConfiguration(10)));
        return engine;
    }

    private static void expectApiFailure(Runnable action, int status, String code) {
        try {
            action.run();
            throw new AssertionError("Expected API failure: " + code);
        } catch (ApiException expected) {
            check(expected.status() == status && expected.code().equals(code),
                    "Unexpected API error: " + expected.code());
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
