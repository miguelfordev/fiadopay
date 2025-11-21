package edu.ucsal.fiadopay.strategies;

import java.math.BigDecimal;

import edu.ucsal.fiadopay.records.InterestResult;

public interface PaymentInterestStrategy {

	InterestResult calculate(BigDecimal amount, Integer installments);
}
