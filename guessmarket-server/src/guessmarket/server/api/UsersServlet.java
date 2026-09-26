package guessmarket.server.api;

import guessmarket.server.ServerApplication;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet("/api/users")
public final class UsersServlet extends ApiServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        UserApiService users = new UserApiService(
                ServerApplication.requireState(request.getServletContext()));
        JsonSupport.write(response, HttpServletResponse.SC_OK, users.publicUsers());
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        rejectUnsupportedMethod(response);
    }
}
