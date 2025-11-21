package edu.ucsal.fiadopay.strategies;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import edu.ucsal.fiadopay.annotations.PaymentHandler;
import edu.ucsal.fiadopay.records.InterestResult;

@Component
@PaymentHandler("BOLETO")
public class BoletoInterestStrategy implements PaymentInterestStrategy{

	@Override
	public InterestResult calculate(BigDecimal amount, Integer installments) {
		// TODO Auto-generated method stub
		return new InterestResult(amount, null);
	}

}
