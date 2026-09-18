package guessmarket.server;

import guessmarket.dto.UserDTO;
import guessmarket.server.api.ApiException;
import guessmarket.server.api.LoginResponse;
import guessmarket.server.api.PublicUserDTO;
import guessmarket.server.api.UserApiService;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class RuntimeUserApiTest {
    public static void main(String[] args) throws Exception {
        loginAndPrivatePublicViews();
        invalidSessionsAndAmounts();
        concurrentDuplicateLoginHasOneWinner();
        concurrentTopUpsLoseNoUpdates();
        System.out.println("RuntimeUserApiTest: all checks passed");
    }

    private static void loginAndPrivatePublicViews() {
        UserApiService users = new UserApiService(new ServerState());
        LoginResponse login = users.login("  Alice  ");
        check(login.success() && !login.sessionToken().isBlank(), "Login did not return a session");
        check(login.user().username().equals("Alice") && login.user().accountBalance() == 0.0,
                "Login returned the wrong initial user state");
        UserDTO toppedUp = users.topUp(login.sessionToken(), 25.0);
        check(toppedUp.accountBalance() == 25.0
                && toppedUp.accountTransactions().getLast().type().equals("TOP_UP"),
                "Top-up or history response is wrong");
        UserDTO own = users.currentUser(login.sessionToken());
        check(own.accountTransactions().size() == 2, "Own-user detail omitted account history");
        List<PublicUserDTO> publicUsers = users.publicUsers();
        check(publicUsers.equals(List.of(new PublicUserDTO("Alice", 25.0, false))),
                "Public user summary exposed incorrect state");
        expectApiFailure(() -> users.login("alice"), 409, "USERNAME_TAKEN");
        expectApiFailure(() -> users.login(" "), 400, "INVALID_USERNAME");
    }

    private static void invalidSessionsAndAmounts() {
        UserApiService users = new UserApiService(new ServerState());
        LoginResponse login = users.login("Alice");
        expectApiFailure(() -> users.currentUser(null), 401, "INVALID_SESSION");
        expectApiFailure(() -> users.currentUser("unknown"), 401, "INVALID_SESSION");
        for (double amount : List.of(0.0, -2.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            expectApiFailure(() -> users.topUp(login.sessionToken(), amount), 400, "INVALID_TOP_UP");
        }
        check(users.currentUser(login.sessionToken()).accountBalance() == 0.0,
                "Rejected API top-up changed balance");
    }

    private static void concurrentDuplicateLoginHasOneWinner() throws Exception {
        UserApiService users = new UserApiService(new ServerState());
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger duplicates = new AtomicInteger();
        Thread[] attempts = new Thread[16];
        for (int index = 0; index < attempts.length; index++) {
            String username = index % 2 == 0 ? "SameName" : " samename ";
            attempts[index] = Thread.ofPlatform().start(() -> {
                try {
                    start.await();
                    users.login(username);
                    successes.incrementAndGet();
                } catch (ApiException error) {
                    if (error.status() == 409 && error.code().equals("USERNAME_TAKEN")) {
                        duplicates.incrementAndGet();
                    } else {
                        throw error;
                    }
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(error);
                }
            });
        }
        start.countDown();
        for (Thread attempt : attempts) attempt.join();
        check(successes.get() == 1 && duplicates.get() == attempts.length - 1,
                "Concurrent duplicate login did not produce exactly one winner");
    }

    private static void concurrentTopUpsLoseNoUpdates() throws Exception {
        UserApiService users = new UserApiService(new ServerState());
        String token = users.login("Alice").sessionToken();
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread[] workers = new Thread[12];
        for (int index = 0; index < workers.length; index++) {
            workers[index] = Thread.ofPlatform().start(() -> {
                try {
                    start.await();
                    for (int topUp = 0; topUp < 100; topUp++) users.topUp(token, 1.0);
                } catch (Throwable error) {
                    failure.compareAndSet(null, error);
                }
            });
        }
        start.countDown();
        for (Thread worker : workers) worker.join();
        if (failure.get() != null) throw new AssertionError("Concurrent top-up failed", failure.get());
        UserDTO user = users.currentUser(token);
        check(user.accountBalance() == 1200.0, "Concurrent top-ups lost updates");
        check(user.accountTransactions().size() == 1201, "Concurrent history lost entries");
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
