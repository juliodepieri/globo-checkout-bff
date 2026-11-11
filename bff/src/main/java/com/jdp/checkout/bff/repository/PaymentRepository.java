package com.jdp.checkout.bff.repository;

import com.jdp.checkout.bff.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
}
