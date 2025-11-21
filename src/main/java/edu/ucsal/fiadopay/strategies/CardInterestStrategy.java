package edu.ucsal.fiadopay.strategies;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Component;

import edu.ucsal.fiadopay.annotations.PaymentHandler;
import edu.ucsal.fiadopay.records.InterestResult;

@Component
@PaymentHandler("CARD")
public class CardInterestStrategy implements PaymentInterestStrategy {

    @Override
    public InterestResult calculate(BigDecimal amount, Integer installments) {
        if (installments != null && installments > 1) {
            Double rate = 1.0; // 1% ao mês

            var base = new BigDecimal("1.03");
            var factor = base.pow(installments);
            var total = amount.multiply(factor).setScale(2, RoundingMode.HALF_UP);

            return new InterestResult(total, rate);
        }

        return new InterestResult(amount, null);
    }
}
