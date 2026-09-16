package guessmarket.domain;

import java.io.Serializable;

public enum EventState implements Serializable {
    NOT_STARTED,
    ACTIVE,
    CLOSED;

    public boolean canTransitionTo(EventState nextState) {
        if (nextState == null) {
            return false;
        }

        return switch (this) {
            case NOT_STARTED -> nextState == ACTIVE;
            case ACTIVE -> nextState == CLOSED;
            case CLOSED -> false;
        };
    }

    public boolean allowsTrading() {
        return this == ACTIVE;
    }
}