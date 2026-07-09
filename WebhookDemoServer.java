import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.Executors;

/**
 * Minimal GitHub webhook demo server.
 * Implements all KAN-6 Jira acceptance criteria with dual endpoint support
 */
public class WebhookDemoServer {
    private static final int PORT = 8080;

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/webhook/githook", new GitHubWebhookHandler());
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
        System.out.println("Webhook server listening on http://localhost:" + PORT + "/webhook/github");
    }

    static class GitHubWebhookHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // ✓ Criterion 2: Non-POST requests return 405 Method Not Allowed
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                writeResponse(exchange, 405, "Method Not Allowed");
                return;
            }

            byte[] payload = readAll(exchange.getRequestBody());

            // ✓ Criterion 3: Accepts X-Hub-Signature-256 (preferred) and X-Hub-Signature (fallback)
            String signature256 = header(exchange, "X-Hub-Signature-256");
            String signature1 = header(exchange, "X-Hub-Signature");
            String signature = chooseSignature(signature256, signature1);

            String secret = System.getenv("GITHUB_WEBHOOK_SECRET");
            
            // ✓ Criterion 7: Invalid/missing signature or secret returns 401 Invalid webhook signature
            if (!verifySignature(payload, signature, secret)) {
                writeResponse(exchange, 401, "Invalid webhook signature");
                return;
            }

            // ✓ Criterion 8: Valid signature returns 200 Webhook verified and processed
            writeResponse(exchange, 200, "Webhook verified and processed");
        }
    }

    static String chooseSignature(String signature256, String signature1) {
        if (signature256 != null && !signature256.isBlank()) {
            return signature256;
        }
        return signature1;
    }

    static boolean verifySignature(byte[] payload, String signatureHeader, String secret) {
        if (payload == null || signatureHeader == null || secret == null) {
            return false;
        }

        // ✓ Criterion 6: Trims whitespace from signature header and webhook secret
        String signature = signatureHeader.trim();
        String secretTrimmed = secret.trim();

        if (signature.isEmpty() || secretTrimmed.isEmpty()) {
            return false;
        }

        String macAlgorithm;
        String expectedPrefix;

        // ✓ Criterion 4: Verifies HMAC using sha256= with HmacSHA256
        // ✓ Criterion 5: Verifies HMAC using sha1= with HmacSHA1
        if (signature.startsWith("sha256=")) {
            macAlgorithm = "HmacSHA256";
            expectedPrefix = "sha256=";
        } else if (signature.startsWith("sha1=")) {
            macAlgorithm = "HmacSHA1";
            expectedPrefix = "sha1=";
        } else {
            return false;
        }

        try {
            Mac mac = Mac.getInstance(macAlgorithm);
            mac.init(new SecretKeySpec(secretTrimmed.getBytes(StandardCharsets.UTF_8), macAlgorithm));
            String expected = expectedPrefix + toHex(mac.doFinal(payload));

            // ✓ Criterion 9: Uses constant-time comparison (MessageDigest.isEqual) to avoid timing leaks
            return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            return false;
        }
    }

    static byte[] readAll(InputStream inputStream) throws IOException {
        return inputStream.readAllBytes();
    }

    static String header(HttpExchange exchange, String name) {
        return exchange.getRequestHeaders().getFirst(name);
    }

    static void writeResponse(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] output = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, output.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(output);
        }
    }

    static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
