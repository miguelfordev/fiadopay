package edu.ucsal.fiadopay.service.fraud;

import org.springframework.stereotype.Component;

import java.util.Random;

@Component
public class FailureSimulator {

    private final Random random = new Random();

    public boolean shouldFail() {
        return random.nextDouble() < 0.6;
    }
}