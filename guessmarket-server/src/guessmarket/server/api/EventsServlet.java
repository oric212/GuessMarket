package guessmarket.server.api;

import guessmarket.server.ServerApplication;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet(urlPatterns = {"/api/events", "/api/events/*"})
public final class EventsServlet extends ApiServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        EventApiService events = new EventApiService(
                ServerApplication.requireState(request.getServletContext()));
        String path = request.getPathInfo();
        if (path == null || path.equals("/") || path.isBlank()) {
            JsonSupport.write(response, HttpServletResponse.SC_OK, events.summaries());
            return;
        }
        if (!path.startsWith("/") || path.substring(1).contains("/")) {
            throw new ApiException(HttpServletResponse.SC_BAD_REQUEST,
                    "MALFORMED_EVENT_PATH", "Event details require exactly one event name");
        }
        String name = path.substring(1);
        try {
            int eventId = Integer.parseInt(name);
            JsonSupport.write(response, HttpServletResponse.SC_OK, events.details(eventId));
        } catch (NumberFormatException ignored) {
            JsonSupport.write(response, HttpServletResponse.SC_OK, events.details(name));
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String[] parts = request.getPathInfo() == null ? new String[0] : request.getPathInfo().split("/");
        if (parts.length != 3) {
            throw new ApiException(400, "MALFORMED_EVENT_ACTION_PATH", "Expected /{eventId}/{action}");
        }
        int eventId;
        try {
            eventId = Integer.parseInt(parts[1]);
            if (eventId <= 0) throw new NumberFormatException();
        } catch (NumberFormatException error) {
            throw new ApiException(400, "INVALID_EVENT_ID", "Event ID must be positive");
        }
        TradingApiService api = new TradingApiService(
                ServerApplication.requireState(request.getServletContext()));
        String token = SessionSupport.token(request);
        Object result = switch (parts[2]) {
            case "start" -> api.start(token, eventId);
            case "purchases" -> api.purchase(token, eventId,
                    JsonSupport.read(request, PurchaseRequest.class));
            case "orders" -> api.order(token, eventId,
                    JsonSupport.read(request, OrderRequest.class));
            case "close" -> api.close(token, eventId,
                    JsonSupport.read(request, CloseEventRequest.class));
            default -> throw new ApiException(404, "UNKNOWN_EVENT_ACTION", "Unknown event action");
        };
        JsonSupport.write(response, HttpServletResponse.SC_OK, result);
    }
}
