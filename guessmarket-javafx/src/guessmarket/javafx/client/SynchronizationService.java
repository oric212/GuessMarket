package guessmarket.javafx.client;

import guessmarket.dto.ChatMessageDTO;
import guessmarket.dto.EventStateDTO;
import javafx.application.Platform;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

public final class SynchronizationService implements AutoCloseable {
    public static final long DEFAULT_INTERVAL_MILLIS = 750;
    private final ScheduledExecutorService scheduler;
    private final long intervalMillis;
    private final PollOperation pollOperation;
    private final Consumer<SyncSnapshot> snapshotConsumer;
    private final Consumer<String> statusConsumer;
    private final Consumer<Runnable> dispatcher;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean inFlight = new AtomicBoolean();
    private final AtomicBoolean outage = new AtomicBoolean();
    private volatile long lastChatSequence;

    public SynchronizationService(
            GuessMarketApiClient api, IntSupplier eventSelection, IntSupplier actionSelection,
            Consumer<SyncSnapshot> snapshots, Consumer<String> statuses) {
        this(DEFAULT_INTERVAL_MILLIS,
                after -> fetch(api, eventSelection, actionSelection, after),
                snapshots, statuses, Platform::runLater);
    }

    SynchronizationService(long intervalMillis, PollOperation operation,
                           Consumer<SyncSnapshot> snapshots, Consumer<String> statuses,
                           Consumer<Runnable> dispatcher) {
        if (intervalMillis <= 0) throw new IllegalArgumentException("Polling interval must be positive");
        this.intervalMillis = intervalMillis;
        this.pollOperation = operation;
        this.snapshotConsumer = snapshots;
        this.statusConsumer = statuses;
        this.dispatcher = dispatcher;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "guessmarket-polling");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start() {
        if (started.compareAndSet(false, true)) {
            scheduler.scheduleWithFixedDelay(this::cycle, 0, intervalMillis, TimeUnit.MILLISECONDS);
        }
    }

    public boolean isRunning() { return started.get() && !scheduler.isShutdown(); }
    boolean isCycleInFlight() { return inFlight.get(); }

    private void cycle() {
        if (!started.get() || !inFlight.compareAndSet(false, true)) return;
        try {
            SyncSnapshot snapshot = pollOperation.poll(lastChatSequence);
            if (!snapshot.chatMessages().isEmpty()) {
                lastChatSequence = snapshot.chatMessages().getLast().sequence();
            }
            dispatcher.accept(() -> snapshotConsumer.accept(snapshot));
            if (outage.compareAndSet(true, false)) {
                dispatcher.accept(() -> statusConsumer.accept("Connection restored."));
            }
        } catch (RuntimeException error) {
            if (outage.compareAndSet(false, true)) {
                String message = error.getMessage() == null ? "Server temporarily unavailable." : error.getMessage();
                dispatcher.accept(() -> statusConsumer.accept(message));
            }
        } finally {
            inFlight.set(false);
        }
    }

    private static SyncSnapshot fetch(
            GuessMarketApiClient api, IntSupplier eventSelection,
            IntSupplier actionSelection, long after) {
        List<guessmarket.dto.EventDTO> events = api.getEventSummaries();
        List<guessmarket.dto.UserDTO> users = api.getUsers();
        Map<Integer, EventStateDTO> details = new LinkedHashMap<>();
        int eventId = eventSelection.getAsInt();
        int actionId = actionSelection.getAsInt();
        if (eventId > 0) details.put(eventId, api.getEventState(eventId));
        if (actionId > 0 && actionId != eventId) details.put(actionId, api.getEventState(actionId));
        List<ChatMessageDTO> chat = api.fetchChatMessages(after);
        return new SyncSnapshot(events, users, details, chat);
    }

    @Override public void close() {
        if (started.getAndSet(false)) scheduler.shutdownNow();
    }

    @FunctionalInterface interface PollOperation { SyncSnapshot poll(long afterSequence); }
}
