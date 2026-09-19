package guessmarket.server;

import guessmarket.dto.ChatMessageDTO;
import guessmarket.server.api.*;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public final class ChatApiTest {
    public static void main(String[] args) throws Exception {
        sendFetchAndValidation();
        concurrentMessagesAreOrderedAndComplete();
        System.out.println("ChatApiTest: all checks passed");
    }

    private static void sendFetchAndValidation() {
        ServerState state = new ServerState();
        UserApiService users = new UserApiService(state);
        ChatApiService chat = new ChatApiService(state);
        String alice = users.login("Alice").sessionToken();
        String bob = users.login("Bob").sessionToken();
        ChatMessageDTO first = chat.send(alice, new ChatSendRequest(" Hello "));
        ChatMessageDTO second = chat.send(bob, new ChatSendRequest("World"));
        check(first.sequence() == 1 && first.senderUsername().equals("Alice")
                && first.message().equals("Hello"), "Sender identity or normalization is wrong");
        check(second.sequence() == 2 && chat.messagesAfter(alice, 0).equals(List.of(first, second)),
                "Full chat fetch is wrong");
        check(chat.messagesAfter(bob, 1).equals(List.of(second)), "Delta fetch is wrong");
        expect(() -> chat.send(alice, new ChatSendRequest("  ")), 400, "BLANK_MESSAGE");
        expect(() -> chat.send(alice, new ChatSendRequest("x".repeat(501))), 400, "MESSAGE_TOO_LONG");
        expect(() -> chat.send("bad", new ChatSendRequest("spoof")), 401, "INVALID_SESSION");
        expect(() -> chat.messagesAfter("bad", 0), 401, "INVALID_SESSION");
        check(chat.messagesAfter(alice, 0).size() == 2, "Rejected messages changed chat state");
    }

    private static void concurrentMessagesAreOrderedAndComplete() throws Exception {
        ServerState state = new ServerState();
        UserApiService users = new UserApiService(state);
        ChatApiService chat = new ChatApiService(state);
        String alice = users.login("Alice").sessionToken();
        String bob = users.login("Bob").sessionToken();
        int workers = 12;
        int each = 40;
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread[] threads = new Thread[workers];
        for (int worker = 0; worker < workers; worker++) {
            String token = worker % 2 == 0 ? alice : bob;
            int id = worker;
            threads[worker] = Thread.ofPlatform().start(() -> {
                try {
                    start.await();
                    for (int index = 0; index < each; index++) {
                        chat.send(token, new ChatSendRequest(id + ":" + index));
                    }
                } catch (Throwable error) { failure.compareAndSet(null, error); }
            });
        }
        start.countDown();
        for (Thread thread : threads) thread.join();
        if (failure.get() != null) throw new AssertionError(failure.get());
        List<ChatMessageDTO> messages = chat.messagesAfter(alice, 0);
        check(messages.size() == workers * each, "Concurrent messages were lost");
        for (int index = 0; index < messages.size(); index++) {
            check(messages.get(index).sequence() == index + 1L,
                    "Chat sequence is duplicated or out of order");
        }
    }

    private static void expect(Runnable action, int status, String code) {
        try { action.run(); throw new AssertionError("Expected " + code); }
        catch (ApiException error) {
            check(error.status() == status && error.code().equals(code), "Unexpected API error");
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
