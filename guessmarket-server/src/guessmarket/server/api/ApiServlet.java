package guessmarket.server.api;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class ApiServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(ApiServlet.class.getName());

    @Override
    protected final void service(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            super.service(request, response);
        } catch (ApiException error) {
            JsonSupport.writeError(response, error.status(), error.code(), error.getMessage());
        } catch (IllegalArgumentException error) {
            JsonSupport.writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "INVALID_REQUEST", safeMessage(error));
        } catch (Exception error) {
            LOGGER.log(Level.SEVERE, "Unhandled API failure", error);
            if (!response.isCommitted()) {
                JsonSupport.writeError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        "INTERNAL_ERROR", "The server could not complete the request");
            }
        }
    }

    protected final void rejectUnsupportedMethod(HttpServletResponse response) throws IOException {
        JsonSupport.writeError(response, HttpServletResponse.SC_METHOD_NOT_ALLOWED,
                "METHOD_NOT_ALLOWED", "This HTTP method is not supported for the resource");
    }

    private static String safeMessage(IllegalArgumentException error) {
        return error.getMessage() == null || error.getMessage().isBlank()
                ? "The request is invalid" : error.getMessage();
    }
}
