package guessmarket.server.api;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet("/api/health")
public final class HealthServlet extends ApiServlet {
    public record HealthResponse(boolean success, String status, String service) {}

    public static HealthResponse response() {
        return new HealthResponse(true, "UP", "GuessMarket Server");
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        JsonSupport.write(response, HttpServletResponse.SC_OK, response());
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        rejectUnsupportedMethod(response);
    }
}
