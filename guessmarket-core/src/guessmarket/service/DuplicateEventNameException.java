package guessmarket.service;

public final class DuplicateEventNameException extends IllegalArgumentException {
    public DuplicateEventNameException(String eventName) {
        super("An event already exists with name: " + eventName);
    }
}
