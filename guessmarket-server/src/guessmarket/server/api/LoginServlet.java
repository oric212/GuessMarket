package guessmarket.server.api;

import guessmarket.server.ServerApplication;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet("/api/login")
public final class LoginServlet extends ApiServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        LoginRequest login = JsonSupport.read(request, LoginRequest.class);
        UserApiService users = new UserApiService(
                ServerApplication.requireState(request.getServletContext()));
        JsonSupport.write(response, HttpServletResponse.SC_CREATED, users.login(login.username()));
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        rejectUnsupportedMethod(response);
    }
}
