package com.jdp.checkout.bff.model;

import java.io.Serializable;
import java.math.BigDecimal;

public record Product(String slug, String name, String description, BigDecimal price, String currency)
        implements Serializable {
}
