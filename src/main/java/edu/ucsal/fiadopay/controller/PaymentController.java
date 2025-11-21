package edu.ucsal.fiadopay.controller;

import edu.ucsal.fiadopay.service.PaymentServiceFacade;

import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {
  private final PaymentServiceFacade service;

  @PostMapping
  @SecurityRequirement(name = "bearerAuth")
  public ResponseEntity<PaymentResponse> create(
          @RequestHeader(name = "Authorization", required = false) String auth,
          @RequestHeader(name = "Idempotency-Key", required = false) String idemKey,
          @RequestBody PaymentRequest req
  ) {
      return ResponseEntity.ok(service.createPayment(auth, idemKey, req));
  }


  @GetMapping("/{id}")
  public ResponseEntity<PaymentResponse> get(
          @RequestHeader(name = "Authorization", required = false) String auth,
          @PathVariable("id") String id
  ) {
      return ResponseEntity.ok(service.getPayment(auth, id));
  }

  @PostMapping("/{id}/refunds")
  @SecurityRequirement(name = "bearerAuth")
  public ResponseEntity<Object> refund(
          @RequestHeader(name = "Authorization", required = false) String auth,
          @PathVariable("id") String id
  ) {
      return ResponseEntity.ok(service.refund(auth, id));
  }

}
