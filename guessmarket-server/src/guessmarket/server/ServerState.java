package guessmarket.server;

import guessmarket.api.Engine;
import guessmarket.dto.UserDTO;
import guessmarket.dto.EventDTO;
import guessmarket.dto.EventStateDTO;
import guessmarket.dto.PurchaseResultDTO;
import guessmarket.dto.OrderSubmissionResultDTO;
import guessmarket.domain.OrderSide;
import guessmarket.service.GuessMarketEngine;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.io.InputStream;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Function;

public final class ServerState {
    private final Engine engine;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);
    private final Map<String, String> usernamesBySessionToken = new LinkedHashMap<>();

    public record Login(String sessionToken, UserDTO user) {}

    public ServerState() {
        this(new GuessMarketEngine());
    }

    public ServerState(Engine engine) {
        this.engine = Objects.requireNonNull(engine, "Engine cannot be null");
    }

    Engine engineIdentity() {
        return engine;
    }

    public <T> T read(Function<Engine, T> operation) {
        Objects.requireNonNull(operation, "Read operation cannot be null");
        lock.readLock().lock();
        try {
            return operation.apply(engine);
        } finally {
            lock.readLock().unlock();
        }
    }

    public <T> T write(Function<Engine, T> operation) {
        Objects.requireNonNull(operation, "Write operation cannot be null");
        lock.writeLock().lock();
        try {
            return operation.apply(engine);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void write(Consumer<Engine> operation) {
        write(engine -> {
            operation.accept(engine);
            return null;
        });
    }

    public Login login(String username) {
        lock.writeLock().lock();
        try {
            UserDTO user = engine.registerUser(username);
            String token = UUID.randomUUID().toString();
            usernamesBySessionToken.put(token, user.username());
            return new Login(token, user);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public UserDTO currentUser(String sessionToken) {
        lock.readLock().lock();
        try {
            return engine.getUser(requireSessionUsername(sessionToken));
        } finally {
            lock.readLock().unlock();
        }
    }

    public UserDTO topUp(String sessionToken, double amount) {
        lock.writeLock().lock();
        try {
            return engine.topUpAccount(requireSessionUsername(sessionToken), amount);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<UserDTO> users() {
        return read(Engine::getUsers);
    }

    public List<EventDTO> importEvents(String sessionToken, InputStream xml) {
        lock.writeLock().lock();
        try {
            return engine.importEventsFromEx03Xml(xml, requireSessionUsername(sessionToken));
        } finally {
            lock.writeLock().unlock();
        }
    }

    public EventStateDTO startEvent(String sessionToken, int eventId) {
        return authenticatedWrite(sessionToken, username -> engine.startEvent(username, eventId));
    }

    public PurchaseResultDTO purchaseShares(
            String sessionToken, int eventId, int optionIndex, int quantity) {
        return authenticatedWrite(sessionToken,
                username -> engine.purchaseShares(username, eventId, optionIndex, quantity));
    }

    public OrderSubmissionResultDTO submitOrder(
            String sessionToken, int eventId, int optionIndex,
            OrderSide side, int quantity, double price) {
        return authenticatedWrite(sessionToken,
                username -> engine.submitOrder(username, eventId, optionIndex, side, quantity, price));
    }

    public EventStateDTO closeEvent(String sessionToken, int eventId, int winningOptionIndex) {
        return authenticatedWrite(sessionToken,
                username -> engine.closeEvent(username, eventId, winningOptionIndex));
    }

    private <T> T authenticatedWrite(String sessionToken, Function<String, T> operation) {
        lock.writeLock().lock();
        try {
            return operation.apply(requireSessionUsername(sessionToken));
        } finally {
            lock.writeLock().unlock();
        }
    }

    private String requireSessionUsername(String sessionToken) {
        if (sessionToken == null || sessionToken.isBlank()) {
            throw new UnknownSessionException();
        }
        String username = usernamesBySessionToken.get(sessionToken.trim());
        if (username == null) throw new UnknownSessionException();
        return username;
    }

    public static final class UnknownSessionException extends RuntimeException {
        public UnknownSessionException() {
            super("The session token is missing or invalid");
        }
    }
}
