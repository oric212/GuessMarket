package guessmarket.server.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

public final class JsonSupport {
    public static final String JSON_CONTENT_TYPE = "application/json";
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    private JsonSupport() {}

    public static String toJson(Object value) {
        return GSON.toJson(value);
    }

    public static <T> T fromJson(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            throw new ApiException(HttpServletResponse.SC_BAD_REQUEST,
                    "MALFORMED_JSON", "Request body must contain JSON");
        }
        try {
            T value = GSON.fromJson(json, type);
            if (value == null) {
                throw new ApiException(HttpServletResponse.SC_BAD_REQUEST,
                        "MALFORMED_JSON", "Request body must contain a JSON value");
            }
            return value;
        } catch (JsonParseException error) {
            throw new ApiException(HttpServletResponse.SC_BAD_REQUEST,
                    "MALFORMED_JSON", "Request body contains invalid JSON");
        }
    }

    public static <T> T read(HttpServletRequest request, Class<T> type) throws IOException {
        try (Reader reader = request.getReader()) {
            StringBuilder body = new StringBuilder();
            char[] buffer = new char[2048];
            int count;
            while ((count = reader.read(buffer)) >= 0) body.append(buffer, 0, count);
            return fromJson(body.toString(), type);
        }
    }

    public static void write(HttpServletResponse response, int status, Object value) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(JSON_CONTENT_TYPE);
        GSON.toJson(value, response.getWriter());
    }

    public static void writeError(
            HttpServletResponse response, int status, String code, String message) throws IOException {
        write(response, status, new ApiError(code, message));
    }
}
