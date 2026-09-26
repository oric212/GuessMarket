package guessmarket.server.api;

import jakarta.servlet.http.HttpServletRequest;

final class SessionSupport {
    static final String HEADER = "X-GuessMarket-Session";

    private SessionSupport() {}

    static String token(HttpServletRequest request) {
        return request.getHeader(HEADER);
    }
}
