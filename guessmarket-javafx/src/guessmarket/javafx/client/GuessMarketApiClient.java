package guessmarket.javafx.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import guessmarket.api.Engine;
import guessmarket.domain.OrderSide;
import guessmarket.dto.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

public final class GuessMarketApiClient implements Engine {
    public static final String SESSION_HEADER = "X-GuessMarket-Session";
    public static final URI DEFAULT_BASE_URI = URI.create("http://localhost:8080/GuessMarket/api/");
    private final HttpClient http;
    private final Gson gson = new GsonBuilder().serializeNulls().create();
    private final URI baseUri;
    private String sessionToken;
    private String username;

    public GuessMarketApiClient() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
                URI.create(System.getProperty("guessmarket.server", DEFAULT_BASE_URI.toString())));
    }

    public GuessMarketApiClient(HttpClient http, URI baseUri) {
        this.http = http;
        String text = baseUri.toString();
        this.baseUri = URI.create(text.endsWith("/") ? text : text + "/");
    }

    public UserDTO login(String requestedUsername) {
        LoginResponse response = sendJson("login", "POST", new LoginRequest(requestedUsername),
                LoginResponse.class, false);
        sessionToken = response.sessionToken();
        username = response.user().username();
        return response.user();
    }

    public String username() { return username; }
    public String sessionToken() { return sessionToken; }

    public List<ChatMessageDTO> fetchChatMessages(long afterSequence) {
        return send("chat/messages?after=" + afterSequence, "GET", null,
                new TypeToken<List<ChatMessageDTO>>() {}.getType(), true);
    }

    public ChatMessageDTO sendChatMessage(String message) {
        return sendJson("chat/messages", "POST", new ChatRequest(message), ChatMessageDTO.class, true);
    }

    @Override public List<EventDTO> getEventSummaries() {
        return send("events", "GET", null, new TypeToken<List<EventDTO>>() {}.getType(), false);
    }
    @Override public EventStateDTO getEventState(int eventId) {
        return sendJson("events/" + eventId, "GET", null, EventStateDTO.class, false);
    }
    @Override public EventStateDTO getEventState(String eventName) {
        return getEventSummaries().stream().filter(e -> e.eventName().equals(eventName)).findFirst()
                .map(e -> getEventState(e.id())).orElseThrow(() -> new IllegalArgumentException("Unknown event"));
    }
    @Override public List<UserDTO> getUsers() {
        List<PublicUser> summaries = send("users", "GET", null,
                new TypeToken<List<PublicUser>>() {}.getType(), false);
        UserDTO own = sessionToken == null ? null : getUser(username);
        return summaries.stream().map(item -> own != null && item.username().equalsIgnoreCase(own.username())
                ? own : new UserDTO(item.username(), item.accountBalance(), false,
                item.marketMaker(), List.of(), List.of(), List.of())).toList();
    }
    @Override public UserDTO getUser(String requestedUsername) {
        if (username != null && username.equalsIgnoreCase(requestedUsername)) {
            return sendJson("user/me", "GET", null, UserDTO.class, true);
        }
        return getUsers().stream().filter(u -> u.username().equalsIgnoreCase(requestedUsername))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown user"));
    }
    @Override public UserDTO topUpAccount(String ignored, double amount) {
        return sendJson("user/account/topup", "POST", new TopUpRequest(amount), UserDTO.class, true);
    }
    @Override public EventStateDTO startEvent(String ignored, int eventId) {
        return sendJson("events/" + eventId + "/start", "POST", new EmptyRequest(), EventStateDTO.class, true);
    }
    @Override public PurchaseResultDTO purchaseShares(String ignored, int eventId, int option, int quantity) {
        return sendJson("events/" + eventId + "/purchases", "POST",
                new PurchaseRequest(option, quantity), PurchaseResultDTO.class, true);
    }
    @Override public OrderSubmissionResultDTO submitOrder(String ignored, int eventId, int option,
            OrderSide side, int quantity, double price) {
        return sendJson("events/" + eventId + "/orders", "POST",
                new OrderRequest(side.name(), option, quantity, price), OrderSubmissionResultDTO.class, true);
    }
    @Override public EventStateDTO closeEvent(String ignored, int eventId, int winner) {
        return sendJson("events/" + eventId + "/close", "POST",
                new CloseRequest(winner), EventStateDTO.class, true);
    }

    @Override public List<EventDTO> importEventsFromEx03Xml(InputStream xml, String ignored) {
        String boundary = "GuessMarket-" + UUID.randomUUID();
        try {
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; "
                    + "filename=\"upload.xml\"\r\nContent-Type: application/xml\r\n\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            xml.transferTo(body);
            body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            HttpRequest request = request("events/upload", true)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build();
            UploadResponse result = execute(request, UploadResponse.class);
            return result.events();
        } catch (IOException error) {
            throw new ApiClientException(0, "CLIENT_IO", "Could not read the XML file");
        }
    }

    @Override public void loadMarketFromXml(String path) {
        try (InputStream input = java.nio.file.Files.newInputStream(java.nio.file.Path.of(path))) {
            importEventsFromEx03Xml(input, username);
        } catch (IOException error) {
            throw new ApiClientException(0, "CLIENT_IO", "Could not read the XML file");
        }
    }

    private <T> T sendJson(String path, String method, Object body, Class<T> type, boolean auth) {
        return send(path, method, body, type, auth);
    }

    private <T> T send(String path, String method, Object body, java.lang.reflect.Type type, boolean auth) {
        HttpRequest.Builder builder = request(path, auth).header("Accept", "application/json");
        if (body == null) builder.method(method, HttpRequest.BodyPublishers.noBody());
        else builder.header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(gson.toJson(body)));
        return execute(builder.build(), type);
    }

    private HttpRequest.Builder request(String path, boolean auth) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(baseUri.resolve(path)).timeout(Duration.ofSeconds(20));
        if (auth) {
            if (sessionToken == null) throw new ApiClientException(401, "NO_SESSION", "Please log in first");
            builder.header(SESSION_HEADER, sessionToken);
        }
        return builder;
    }

    private <T> T execute(HttpRequest request, java.lang.reflect.Type type) {
        try {
            HttpResponse<String> response = http.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                ApiError error;
                try { error = gson.fromJson(response.body(), ApiError.class); }
                catch (RuntimeException ignored) { error = null; }
                throw new ApiClientException(response.statusCode(), error == null ? "HTTP_ERROR" : error.code(),
                        error == null || error.message() == null ? "Server request failed" : error.message());
            }
            return gson.fromJson(response.body(), type);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new ApiClientException(0, "INTERRUPTED", "The request was interrupted");
        } catch (IOException error) {
            throw new ApiClientException(0, "SERVER_UNREACHABLE", "Could not connect to GuessMarket server");
        }
    }

    @Override public UserDTO registerUser(String username) { return login(username); }
    @Override public EventStateDTO startEvent(String u, String name) { return startEvent(u, id(name)); }
    @Override public PurchaseResultDTO purchaseShares(String u, String name, int o, int q) { return purchaseShares(u, id(name), o, q); }
    @Override public OrderSubmissionResultDTO submitOrder(String u, String n, int o, OrderSide s, int q, double p) { return submitOrder(u, id(n), o, s, q, p); }
    @Override public EventStateDTO closeEvent(String u, String n, int o) { return closeEvent(u, id(n), o); }
    @Override public EventStateDTO createEvent(CreateEventRequest request) { throw new UnsupportedOperationException("Use XML upload in EX03"); }
    @Override public void saveState(String path) { throw new UnsupportedOperationException("Server state is authoritative"); }
    @Override public void loadState(String path) { throw new UnsupportedOperationException("Server state is authoritative"); }
    private int id(String name) { return getEventSummaries().stream().filter(e -> e.eventName().equals(name)).findFirst().orElseThrow().id(); }

    private record LoginRequest(String username) {}
    private record LoginResponse(boolean success, String sessionToken, UserDTO user) {}
    private record TopUpRequest(double amount) {}
    private record PurchaseRequest(int optionIndex, int quantity) {}
    private record OrderRequest(String side, int optionIndex, int quantity, double price) {}
    private record CloseRequest(int winningOptionIndex) {}
    private record EmptyRequest() {}
    private record PublicUser(String username, double accountBalance, boolean marketMaker) {}
    private record UploadResponse(boolean success, int eventsAdded, List<EventDTO> events) {}
    private record ApiError(boolean success, String code, String message) {}
    private record ChatRequest(String message) {}
}
