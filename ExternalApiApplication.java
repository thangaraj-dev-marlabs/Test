package com.example.externalapi;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

@SpringBootApplication
public class ExternalApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExternalApiApplication.class, args);
    }
}

@RestController
@RequestMapping("/api/external")
class ExternalApiController {

    private final HttpClient httpClient;

    @Value("${external.api.base-url:}")
    private String baseUrl;

    @Value("${external.api.key:}")
    private String apiKey;

    @Value("${external.api.timeout-seconds:5}")
    private long timeoutSeconds;

    ExternalApiController() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    }

    @GetMapping(value = "/search", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> search(@RequestParam(name = "q", required = false) String query) {
        validateConfig();
        validateQuery(query);

        try {
            String encodedQuery = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8);
            URI uri = URI.create(baseUrl + "?q=" + encodedQuery);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            int status = response.statusCode();
            String body = response.body() == null ? "" : response.body();

            if (status == 200) {
                return ResponseEntity.ok(body);
            }

            if (status == 401 || status == 403) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "External API authentication failed"
                );
            }

            if (status == 404) {
                throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "External resource not found"
                );
            }

            throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "External API returned unexpected status: " + status
            );

        } catch (HttpTimeoutException ex) {
            throw new ResponseStatusException(
                HttpStatus.GATEWAY_TIMEOUT,
                "External API request timed out"
            );
        } catch (IOException | InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "External API call failed"
            );
        }
    }

    private void validateQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Query parameter 'q' is required"
            );
        }
    }

    private void validateConfig() {
        if (baseUrl == null || baseUrl.isBlank() || apiKey == null || apiKey.isBlank()) {
            throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "External API configuration is missing"
            );
        }
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode())
            .body(Map.of("error", ex.getReason() == null ? "Request failed" : ex.getReason()));
    }

    @ExceptionHandler({
        org.springframework.web.HttpRequestMethodNotSupportedException.class,
        MethodArgumentTypeMismatchException.class,
        ErrorResponseException.class
    })
    public ResponseEntity<Map<String, String>> handleFrameworkErrors(Exception ex) {
        if (ex instanceof org.springframework.web.HttpRequestMethodNotSupportedException) {
            return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(Map.of("error", "Method not allowed"));
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(Map.of("error", "Bad request"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of("error", "Internal server error"));
    }
}
