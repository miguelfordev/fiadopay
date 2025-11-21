package edu.ucsal.fiadopay.service.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;


import edu.ucsal.fiadopay.annotations.PaymentHandler;
import edu.ucsal.fiadopay.controller.PaymentRequest;
import edu.ucsal.fiadopay.domain.Merchant;
import edu.ucsal.fiadopay.domain.Payment;
import edu.ucsal.fiadopay.repo.PaymentRepository;
import edu.ucsal.fiadopay.service.fraud.FailureSimulator;
import edu.ucsal.fiadopay.service.webhook.WebhookProcessor;
import edu.ucsal.fiadopay.strategies.PaymentInterestStrategy;
import edu.ucsal.fiadopay.records.InterestResult;

@Service
public class PaymentCreatorService {

    private final PaymentRepository payments;
    private final FailureSimulator failureSimulator;
    private final List<PaymentInterestStrategy> paymentstrategies;
    private final ExecutorService executor;
    private final WebhookProcessor webhookprocessor;

    @Value("${fiadopay.webhook-secret}")
    private String secret;

    public PaymentCreatorService(
            PaymentRepository payments,
            FailureSimulator failureSimulator,
            List<PaymentInterestStrategy> paymentstrategies,
            ExecutorService executor,
            WebhookProcessor webhookprocessor
    ) {
        this.payments = payments;
        this.failureSimulator = failureSimulator;
        this.paymentstrategies = paymentstrategies;
        this.executor = executor;
        this.webhookprocessor = webhookprocessor;
    }

    @Transactional
    public Payment createPayment(Merchant merchant, String idemKey, PaymentRequest req) {
        var mid = merchant.getId();

        if (idemKey != null) {
            var existing = payments.findByIdempotencyKeyAndMerchantId(idemKey, mid);
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        Double interest = null;
        BigDecimal total = req.amount();
        
        for(PaymentInterestStrategy paymentstrategy : this.paymentstrategies) {
        	Class<?> classe = paymentstrategy.getClass();
        	if(classe.isAnnotationPresent(PaymentHandler.class));
        	
        	PaymentHandler anotacao = classe.getAnnotation(PaymentHandler.class);
        	if(anotacao != null && anotacao.value().equalsIgnoreCase(req.method())) {
        		InterestResult resultado = paymentstrategy.calculate(total, req.installments());        		
        		total = resultado.finalAmount();
        		interest = resultado.appliedRate();
        	}	
        }

        var payment = Payment.builder()
                .id("pay_" + UUID.randomUUID().toString().substring(0, 8))
                .merchantId(mid)
                .method(req.method().toUpperCase())
                .amount(req.amount())
                .currency(req.currency())
                .installments(req.installments() == null ? 1 : req.installments())
                .monthlyInterest(interest)
                .totalWithInterest(total)
                .status(Payment.Status.PENDING)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .idempotencyKey(idemKey)
                .metadataOrderId(req.metadataOrderId())
                .build();

        payments.save(payment);

        executor.submit(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {}

            boolean approved = !failureSimulator.shouldFail();
            payment.setStatus(approved ? Payment.Status.APPROVED : Payment.Status.DECLINED);
            payment.setUpdatedAt(Instant.now());
            payments.save(payment);
            webhookprocessor.sendWebhook(payment);

        });

        return payment;
    }

    @Transactional
    public java.util.Map<String, Object> refund(String auth, String paymentId, Merchant merchant) {
        var p = payments.findById(paymentId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!merchant.getId().equals(p.getMerchantId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        p.setStatus(Payment.Status.REFUNDED);
        p.setUpdatedAt(Instant.now());
        payments.save(p);
        webhookprocessor.sendWebhook(p);
        return java.util.Map.of("id", "ref_" + UUID.randomUUID(), "status", "PENDING");
    }
}
