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
        JsonSupport.write(response, HttpServletResponse.SC_OK, events.details(name));
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        rejectUnsupportedMethod(response);
    }
}
