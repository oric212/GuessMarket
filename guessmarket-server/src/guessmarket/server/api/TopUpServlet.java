package guessmarket.server.api;

import guessmarket.server.ServerApplication;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet("/api/user/account/topup")
public final class TopUpServlet extends ApiServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        TopUpRequest topUp = JsonSupport.read(request, TopUpRequest.class);
        UserApiService users = new UserApiService(
                ServerApplication.requireState(request.getServletContext()));
        JsonSupport.write(response, HttpServletResponse.SC_OK,
                users.topUp(SessionSupport.token(request), topUp.amount()));
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        rejectUnsupportedMethod(response);
    }
}
