package edu.ucsal.fiadopay.service;

import org.springframework.stereotype.Service;

import edu.ucsal.fiadopay.controller.PaymentRequest;
import edu.ucsal.fiadopay.controller.PaymentResponse;
import edu.ucsal.fiadopay.domain.Merchant;
import edu.ucsal.fiadopay.service.auth.MerchantAuthService;
import edu.ucsal.fiadopay.service.payment.PaymentCreatorService;
import edu.ucsal.fiadopay.service.payment.PaymentQueryService;

@Service
public class PaymentServiceFacade {

    private final MerchantAuthService merchantAuth;
    private final PaymentCreatorService creator;
    private final PaymentQueryService query;

    public PaymentServiceFacade(
            MerchantAuthService merchantAuth,
            PaymentCreatorService creator,
            PaymentQueryService query
    ) {
        this.merchantAuth = merchantAuth;
        this.creator = creator;
        this.query = query;
    }

    public PaymentResponse createPayment(String auth, String idemKey, PaymentRequest req) {
        Merchant merchant = merchantAuth.merchantFromAuth(auth);
        var p = creator.createPayment(merchant, idemKey, req);
        return new PaymentResponse(
            p.getId(),
            p.getStatus().name(),
            p.getMethod(),
            p.getAmount(),
            p.getInstallments(),
            p.getMonthlyInterest(),
            p.getTotalWithInterest()
        );
    }

    public PaymentResponse getPayment(String auth, String paymentId) {
        Merchant merchant = merchantAuth.merchantFromAuth(auth);
        var p = query.getPayment(merchant, paymentId);
        return new PaymentResponse(
            p.getId(),
            p.getStatus().name(),
            p.getMethod(),
            p.getAmount(),
            p.getInstallments(),
            p.getMonthlyInterest(),
            p.getTotalWithInterest()
        );
    }

    public java.util.Map<String,Object> refund(String auth, String paymentId) {
        Merchant merchant = merchantAuth.merchantFromAuth(auth);
        return creator.refund(auth, paymentId, merchant);
    }
}
