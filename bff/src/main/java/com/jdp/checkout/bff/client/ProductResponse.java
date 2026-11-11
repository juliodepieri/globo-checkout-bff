package com.jdp.checkout.bff.client;

import java.math.BigDecimal;

/** Contract of the external products service. */
record ProductResponse(String sku, String title, String summary, BigDecimal price, String currency, boolean active) {
}
