package guessmarket.server.api;

public record ApiError(boolean success, String code, String message) {
    public ApiError(String code, String message) {
        this(false, code, message);
    }
}
