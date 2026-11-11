package com.jdp.checkout.mock;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
class CatalogController {

    record ProductDto(String sku, String title, String summary, BigDecimal price, String currency, boolean active) {
    }

    private static final List<ProductDto> CATALOG = List.of(
            new ProductDto("basico", "Plano Básico", "Assinatura mensal com catálogo essencial", new BigDecimal("19.90"), "BRL", true),
            new ProductDto("premium", "Plano Premium", "Assinatura mensal com catálogo completo", new BigDecimal("39.90"), "BRL", true),
            new ProductDto("familia", "Plano Família", "Assinatura mensal para até 5 perfis", new BigDecimal("59.90"), "BRL", true));

    @GetMapping("/catalog/products")
    List<ProductDto> products() {
        return CATALOG;
    }
}
