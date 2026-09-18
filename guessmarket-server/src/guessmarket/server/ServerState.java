package guessmarket.server;

import guessmarket.api.Engine;
import guessmarket.service.GuessMarketEngine;

import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Function;

public final class ServerState {
    private final Engine engine;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);

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
}
