package com.example.webhook.controller;

import com.example.webhook.service.WebhookVerificationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class WebhookController {

    private final WebhookVerificationService verificationService;

    public WebhookController(WebhookVerificationService verificationService) {
        this.verificationService = verificationService;
    }

    @PostMapping("/githook")
    public ResponseEntity<String> handleWebhook(
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature256,
            @RequestHeader(value = "X-Hub-Signature", required = false) String signature1,
            @RequestBody String payload) {

        boolean ok = verificationService.verify(signature256, signature1, payload);
        if (!ok) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid webhook signature");
        }

        // process payload here
        return ResponseEntity.ok("Webhook verified and processed");
    }
}
