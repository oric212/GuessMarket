package guessmarket.server.api;

import guessmarket.dto.UserDTO;
import guessmarket.server.ServerState;

import java.util.List;

public final class UserApiService {
    private final ServerState state;

    public UserApiService(ServerState state) {
        this.state = state;
    }

    public LoginResponse login(String username) {
        if (username == null || username.isBlank()) {
            throw new ApiException(400, "INVALID_USERNAME", "Username cannot be blank");
        }
        try {
            ServerState.Login login = state.login(username);
            return new LoginResponse(true, login.sessionToken(), login.user());
        } catch (IllegalArgumentException error) {
            throw new ApiException(409, "USERNAME_TAKEN",
                    "Username is already in use: " + username.trim());
        }
    }

    public List<PublicUserDTO> publicUsers() {
        return state.users().stream()
                .map(user -> new PublicUserDTO(
                        user.username(), user.accountBalance(), user.marketMaker()))
                .toList();
    }

    public UserDTO currentUser(String sessionToken) {
        try {
            return state.currentUser(sessionToken);
        } catch (ServerState.UnknownSessionException error) {
            throw invalidSession();
        }
    }

    public UserDTO topUp(String sessionToken, double amount) {
        if (!Double.isFinite(amount) || amount <= 0.0) {
            throw new ApiException(400, "INVALID_TOP_UP",
                    "Top-up amount must be finite and greater than zero");
        }
        try {
            return state.topUp(sessionToken, amount);
        } catch (ServerState.UnknownSessionException error) {
            throw invalidSession();
        }
    }

    private ApiException invalidSession() {
        return new ApiException(401, "INVALID_SESSION", "The session token is missing or invalid");
    }
}
