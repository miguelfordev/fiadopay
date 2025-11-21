package edu.ucsal.fiadopay.service.payment;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import edu.ucsal.fiadopay.domain.Merchant;
import edu.ucsal.fiadopay.domain.Payment;
import edu.ucsal.fiadopay.repo.PaymentRepository;

@Service
public class PaymentQueryService {

    private final PaymentRepository payments;

    public PaymentQueryService(PaymentRepository payments) {
        this.payments = payments;
    }

    public Payment getPayment(Merchant merchant, String paymentId) {
        var p = payments.findById(paymentId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!p.getMerchantId().equals(merchant.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return p;
    }
}
