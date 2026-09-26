package guessmarket.server.api;

import guessmarket.dto.UserDTO;

public record LoginResponse(boolean success, String sessionToken, UserDTO user) {
}
