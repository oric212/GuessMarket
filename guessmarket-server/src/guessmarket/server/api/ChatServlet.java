package guessmarket.server.api;

import guessmarket.server.ServerApplication;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet("/api/chat/messages")
public final class ChatServlet extends ApiServlet {
    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        long after;
        try {
            String value = request.getParameter("after");
            after = value == null || value.isBlank() ? 0 : Long.parseLong(value);
        } catch (NumberFormatException error) {
            throw new ApiException(400, "INVALID_SEQUENCE", "after must be a whole number");
        }
        ChatApiService chat = new ChatApiService(ServerApplication.requireState(request.getServletContext()));
        JsonSupport.write(response, 200, chat.messagesAfter(SessionSupport.token(request), after));
    }

    @Override protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ChatApiService chat = new ChatApiService(ServerApplication.requireState(request.getServletContext()));
        JsonSupport.write(response, 201, chat.send(
                SessionSupport.token(request), JsonSupport.read(request, ChatSendRequest.class)));
    }
}
