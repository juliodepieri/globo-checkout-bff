package com.jdp.checkout.mock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Stand-in for the external Products and Payment Methods services.
 * Its contract is deliberately different from the BFF contract (sku/title, code/displayName)
 * so the BFF clients have real translation work to do.
 */
@SpringBootApplication
public class MockServicesApplication {

    public static void main(String[] args) {
        SpringApplication.run(MockServicesApplication.class, args);
    }
}
