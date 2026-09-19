package guessmarket.javafx.client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import guessmarket.domain.OrderSide;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class GuessMarketApiClientTest {
    public static void main(String[] args) throws Exception {
        List<Request> requests = new ArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/", exchange -> respond(exchange, requests));
        server.start();
        try {
            URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api/");
            GuessMarketApiClient client = new GuessMarketApiClient(HttpClient.newHttpClient(), base);
            client.login("Alice");
            check(client.sessionToken().equals("token-1") && client.username().equals("Alice"),
                    "Login decoding failed");
            client.getEventSummaries();
            client.getUser("Alice");
            client.getUsers();
            client.topUpAccount("ignored", 100);
            client.startEvent("ignored", 7);
            client.purchaseShares("ignored", 7, 1, 2);
            client.submitOrder("ignored", 7, 2, OrderSide.BUY, 3, 4.5);
            client.closeEvent("ignored", 7, 1);
            client.importEventsFromEx03Xml(new java.io.ByteArrayInputStream(
                    "<Guess-Market/>".getBytes(StandardCharsets.UTF_8)), "ignored");

            check(requests.stream().filter(r -> r.path().startsWith("/api/user/")
                            || r.path().matches("/api/events/\\d+/(start|purchases|orders|close)")
                            || r.path().equals("/api/events/upload"))
                            .allMatch(r -> "token-1".equals(r.session())),
                    "Authenticated request omitted the session header");
            check(paths(requests).containsAll(List.of("/api/events", "/api/user/me", "/api/users",
                    "/api/user/account/topup", "/api/events/7/start", "/api/events/7/purchases",
                    "/api/events/7/orders", "/api/events/7/close", "/api/events/upload")),
                    "One or more API routes were not called");
            check(requests.stream().filter(r -> r.path().equals("/api/events/upload")).findFirst()
                            .orElseThrow().body().contains("<Guess-Market/>"),
                    "Multipart upload omitted actual XML content");

            try {
                client.getEventState(999);
                throw new AssertionError("Structured API error was not raised");
            } catch (ApiClientException expected) {
                check(expected.status() == 404 && expected.code().equals("EVENT_NOT_FOUND")
                        && expected.getMessage().equals("No event"), "Structured error decoding failed");
            }
        } finally {
            server.stop(0);
        }
        System.out.println("GuessMarketApiClientTest: all checks passed");
    }

    private static void respond(HttpExchange exchange, List<Request> requests) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String path = exchange.getRequestURI().getPath();
        requests.add(new Request(path, exchange.getRequestHeaders()
                .getFirst(GuessMarketApiClient.SESSION_HEADER), body));
        String json;
        int status = 200;
        if (path.equals("/api/login")) {
            status = 201;
            json = "{\"success\":true,\"sessionToken\":\"token-1\",\"user\":" + user() + "}";
        } else if (path.equals("/api/events/999")) {
            status = 404;
            json = "{\"success\":false,\"code\":\"EVENT_NOT_FOUND\",\"message\":\"No event\"}";
        } else if (path.equals("/api/events") || path.equals("/api/users")) {
            json = "[]";
        } else if (path.equals("/api/user/me") || path.equals("/api/user/account/topup")) {
            json = user();
        } else if (path.equals("/api/events/upload")) {
            status = 201; json = "{\"success\":true,\"eventsAdded\":0,\"events\":[]}";
        } else if (path.endsWith("/start") || path.endsWith("/close")) {
            json = "{\"id\":7,\"eventName\":\"E\",\"currentEventAccountBalance\":0,"
                    + "\"totalCommissionCollected\":0,\"optionStateDTOList\":[],\"trades\":[],"
                    + "\"eventState\":\"ACTIVE\",\"winningOption\":null,\"description\":\"D\","
                    + "\"tradingMethod\":\"LMSR\",\"commissionPercentage\":0,"
                    + "\"commissionMethod\":\"ON_PURCHASE\",\"marketMakerUsername\":\"Alice\","
                    + "\"options\":[],\"lmsrDetails\":null,\"orderBookDetails\":null,\"participants\":[]}";
        } else if (path.endsWith("/purchases")) {
            json = "{\"id\":7,\"eventName\":\"E\",\"totalPricePaid\":1,"
                    + "\"purchaseCost\":1,\"commission\":0,\"eventState\":\"ACTIVE\"}";
        } else if (path.endsWith("/orders")) {
            json = "{\"eventId\":7,\"optionName\":\"No\",\"side\":\"BUY\","
                    + "\"originalQuantity\":3,\"remainingQuantity\":3,\"limitPrice\":4.5,"
                    + "\"executions\":[],\"mintExecutions\":[]}";
        } else {
            json = "{}";
        }
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static String user() {
        return "{\"username\":\"Alice\",\"accountBalance\":100,\"blocked\":false,"
                + "\"marketMaker\":false,\"marketMakerEventIds\":[],\"participations\":[],"
                + "\"accountTransactions\":[]}";
    }

    private static List<String> paths(List<Request> requests) {
        return requests.stream().map(Request::path).toList();
    }

    private record Request(String path, String session, String body) {}
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
