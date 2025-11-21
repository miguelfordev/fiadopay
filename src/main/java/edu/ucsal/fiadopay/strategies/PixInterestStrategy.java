package edu.ucsal.fiadopay.strategies;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import edu.ucsal.fiadopay.annotations.PaymentHandler;
import edu.ucsal.fiadopay.records.InterestResult;

@Component
@PaymentHandler("PIX")
public class PixInterestStrategy implements PaymentInterestStrategy{

	@Override
	public InterestResult calculate(BigDecimal amount, Integer installments) {
		// TODO Auto-generated method stub
		return new InterestResult(amount, null);
	}

}
