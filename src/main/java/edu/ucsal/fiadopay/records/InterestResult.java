package edu.ucsal.fiadopay.records;

import java.math.BigDecimal;

public record InterestResult(
    BigDecimal finalAmount, // O valor final (com ou sem juros)
    Double appliedRate      // A taxa usada (null se não teve juros)
) {}
