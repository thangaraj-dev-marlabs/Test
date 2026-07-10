package com.example.webhook.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class WebhookVerificationService {

    @Value("${webhook.secret:}")
    private String webhookSecret;

    public boolean verify(String signature256Header, String signature1Header, String payload) {
        String secret = safeTrim(webhookSecret);

        // Missing/blank secret => invalid
        if (secret.isEmpty()) {
            return false;
        }

        // Preferred: X-Hub-Signature-256
        String sig256 = safeTrim(signature256Header);
        if (!sig256.isEmpty()) {
            return verifyByHeader(sig256, payload, secret, "sha256=", "HmacSHA256");
        }

        // Fallback: X-Hub-Signature
        String sig1 = safeTrim(signature1Header);
        if (!sig1.isEmpty()) {
            return verifyByHeader(sig1, payload, secret, "sha1=", "HmacSHA1");
        }

        // Missing both signature headers
        return false;
    }

    private boolean verifyByHeader(String headerValue,
                                   String payload,
                                   String secret,
                                   String expectedPrefix,
                                   String algorithm) {
        // Header must start with correct prefix
        if (!headerValue.startsWith(expectedPrefix)) {
            return false;
        }

        String receivedHex = headerValue.substring(expectedPrefix.length()).trim();
        if (receivedHex.isEmpty()) {
            return false;
        }

        String computedHex = hmacHex(payload, secret, algorithm);
        if (computedHex == null) {
            return false;
        }

        byte[] receivedBytes = receivedHex.getBytes(StandardCharsets.UTF_8);
        byte[] computedBytes = computedHex.getBytes(StandardCharsets.UTF_8);

        // Constant-time comparison
        return MessageDigest.isEqual(receivedBytes, computedBytes);
    }

    private String hmacHex(String payload, String secret, String algorithm) {
        try {
            Mac mac = Mac.getInstance(algorithm);
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), algorithm);
            mac.init(keySpec);
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return toHex(digest);
        } catch (Exception e) {
            return null;
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }
}
