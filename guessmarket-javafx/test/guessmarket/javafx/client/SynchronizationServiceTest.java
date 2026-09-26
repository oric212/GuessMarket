package guessmarket.javafx.client;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class SynchronizationServiceTest {
    public static void main(String[] args) throws Exception {
        startsStopsAndPreventsOverlap();
        outageIsReportedOnceAndRecoveryContinues();
        System.out.println("SynchronizationServiceTest: all checks passed");
    }

    private static void startsStopsAndPreventsOverlap() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger concurrent = new AtomicInteger();
        AtomicInteger maxConcurrent = new AtomicInteger();
        CountDownLatch delivered = new CountDownLatch(2);
        SynchronizationService service = new SynchronizationService(
                20,
                after -> {
                    calls.incrementAndGet();
                    int active = concurrent.incrementAndGet();
                    maxConcurrent.accumulateAndGet(active, Math::max);
                    try { Thread.sleep(60); } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                    } finally { concurrent.decrementAndGet(); }
                    return new SyncSnapshot(List.of(), List.of(), Map.of(), List.of());
                },
                snapshot -> delivered.countDown(), ignored -> {}, Runnable::run);
        service.start();
        service.start();
        check(delivered.await(2, TimeUnit.SECONDS), "Polling did not deliver updated state");
        check(maxConcurrent.get() == 1, "Poll cycles overlapped");
        service.close();
        int stoppedAt = calls.get();
        Thread.sleep(150);
        check(!service.isRunning() && calls.get() == stoppedAt, "Polling did not stop cleanly");
    }

    private static void outageIsReportedOnceAndRecoveryContinues() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        CopyOnWriteArrayList<String> statuses = new CopyOnWriteArrayList<>();
        CountDownLatch success = new CountDownLatch(1);
        SynchronizationService service = new SynchronizationService(
                20,
                after -> {
                    int attempt = attempts.incrementAndGet();
                    if (attempt <= 3) throw new ApiClientException(0, "DOWN", "Server unavailable");
                    return new SyncSnapshot(List.of(), List.of(), Map.of(), List.of());
                }, snapshot -> success.countDown(), statuses::add, Runnable::run);
        service.start();
        check(success.await(2, TimeUnit.SECONDS), "Polling did not recover after an outage");
        service.close();
        check(statuses.stream().filter("Server unavailable"::equals).count() == 1,
                "Outage produced repeated error spam");
        check(statuses.contains("Connection restored."), "Recovery status was not reported");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
