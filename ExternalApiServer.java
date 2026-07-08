import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;

public class ExternalApiServer {
    private static final int PORT = 8082;
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/external/user", ExternalApiServer::handleUserRequest);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
        System.out.println("External API server listening on http://localhost:" + PORT + "/external/user?id=123");
    }

    private static void handleUserRequest(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            writeResponse(exchange, 405, "Method Not Allowed");
            return;
        }

        String userId = getQueryParam(exchange.getRequestURI(), "id");
        if (userId == null || userId.isBlank()) {
            writeResponse(exchange, 400, "Missing required query parameter: id");
            return;
        }

        String apiBaseUrl = getenvTrimmed("EXTERNAL_API_BASE_URL");
        String apiKey = getenvTrimmed("EXTERNAL_API_KEY");

        if (apiBaseUrl.isEmpty() || apiKey.isEmpty()) {
            writeResponse(exchange, 500, "Missing external API configuration");
            return;
        }

        try {
            HttpResponse<String> apiResponse = fetchUser(apiBaseUrl, apiKey, userId);

            if (apiResponse.statusCode() == 200) {
                writeJson(exchange, 200, apiResponse.body());
                return;
            }

            if (apiResponse.statusCode() == 401 || apiResponse.statusCode() == 403) {
                writeResponse(exchange, 502, "External API authentication failed");
                return;
            }

            if (apiResponse.statusCode() == 404) {
                writeResponse(exchange, 404, "External resource not found");
                return;
            }

            writeResponse(exchange, 502, "External API error: " + apiResponse.statusCode());
        } catch (HttpConnectTimeoutException exception) {
            writeResponse(exchange, 504, "External API connection timed out");
        } catch (java.net.http.HttpTimeoutException exception) {
            writeResponse(exchange, 504, "External API request timed out");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            writeResponse(exchange, 500, "Request interrupted");
        } catch (Exception exception) {
            writeResponse(exchange, 500, "Failed to call external API");
        }
    }

    private static HttpResponse<String> fetchUser(
        String apiBaseUrl,
        String apiKey,
        String userId
    ) throws IOException, InterruptedException {
        String encodedUserId = URLEncoder.encode(userId, StandardCharsets.UTF_8);
        String url = apiBaseUrl + "/users/" + encodedUserId;

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(TIMEOUT)
            .header("Accept", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .GET()
            .build();

        return HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .build()
            .send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String getQueryParam(URI uri, String key) {
        String query = uri.getRawQuery();
        if (query == null || query.isEmpty()) {
            return null;
        }

        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2 && key.equals(parts[0])) {
                return URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
            }
        }

        return null;
    }

    private static String getenvTrimmed(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value.trim();
    }

    private static void writeJson(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] output = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, output.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(output);
        }
    }

    private static void writeResponse(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] output = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, output.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(output);
        }
    }
}
