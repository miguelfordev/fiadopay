package edu.ucsal.fiadopay.service.webhook;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import edu.ucsal.fiadopay.repo.MerchantRepository;
import edu.ucsal.fiadopay.repo.WebhookDeliveryRepository;
import edu.ucsal.fiadopay.domain.Payment;
import edu.ucsal.fiadopay.domain.WebhookDelivery;

@Service
public class WebhookProcessor {
	
	private final ExecutorService executor;
    private final MerchantRepository merchants;
    private final WebhookDeliveryRepository deliveries;
    private final ObjectMapper objectMapper;
    private final String secret;

    public WebhookProcessor(
            MerchantRepository merchants,
            WebhookDeliveryRepository deliveries,
            ExecutorService executor,
            ObjectMapper objectMapper,
            @Value("${fiadopay.webhook-secret}") String secret
    ) {
        this.merchants = merchants;
        this.executor = executor;
        this.deliveries = deliveries;
        this.objectMapper = objectMapper;
        this.secret = secret;
    }

    public void sendWebhook(Payment payment) {

        var merchant = merchants.findById(payment.getMerchantId()).orElse(null);
        if (merchant == null ||
                merchant.getWebhookUrl() == null ||
                merchant.getWebhookUrl().isBlank())
            return;

        String payload;
        String eventId = "evt_" + UUID.randomUUID().toString().substring(0, 8);

        try {
            var data = Map.of(
                    "paymentId", payment.getId(),
                    "status", payment.getStatus().name(),
                    "occurredAt", Instant.now().toString()
            );

            var event = Map.of(
                    "id", eventId,
                    "type", "payment.updated",
                    "data", data
            );

            payload = objectMapper.writeValueAsString(event);

        } catch (Exception e) {
            return;
        }

        var signature = hmacSha256(secret, payload);

        var delivery = deliveries.save(WebhookDelivery.builder()
                .eventId(eventId)
                .eventType("payment.updated")
                .paymentId(payment.getId())
                .targetUrl(merchant.getWebhookUrl())
                .signature(signature)
                .payload(payload)
                .attempts(0)
                .delivered(false)
                .lastAttemptAt(null)
                .build()
        );

        executor.submit(() -> deliverWithRetry(delivery.getId()));
    }

    private void deliverWithRetry(Long deliveryId) {

        for (int attempt = 1; attempt <= 5; attempt++) {

            var d = deliveries.findById(deliveryId).orElse(null);
            if (d == null) return;

            try {
                var client = HttpClient.newHttpClient();

                var req = HttpRequest.newBuilder(URI.create(d.getTargetUrl()))
                        .header("Content-Type", "application/json")
                        .header("X-Event-Type", d.getEventType())
                        .header("X-Signature", d.getSignature())
                        .POST(HttpRequest.BodyPublishers.ofString(d.getPayload()))
                        .build();

                var res = client.send(req, HttpResponse.BodyHandlers.ofString());

                d.setAttempts(attempt);
                d.setLastAttemptAt(Instant.now());
                d.setDelivered(res.statusCode() >= 200 && res.statusCode() < 300);

                deliveries.save(d);

                if (d.isDelivered())
                    return;

            } catch (Exception e) {
                var d2 = deliveries.findById(deliveryId).orElse(null);
                if (d2 == null) return;

                d2.setAttempts(attempt);
                d2.setLastAttemptAt(Instant.now());
                d2.setDelivered(false);
                deliveries.save(d2);
            }

            try {
                Thread.sleep(1000L * attempt);
            } catch (InterruptedException ignored) {}
        }
    }
   
    
    public static String hmacSha256(String secret, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));

            byte[] hash = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
}