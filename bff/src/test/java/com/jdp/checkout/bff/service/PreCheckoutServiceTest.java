package com.jdp.checkout.bff.service;

import com.jdp.checkout.bff.repository.CartRepository;
import com.jdp.checkout.bff.client.PaymentMethodsClient;
import com.jdp.checkout.bff.client.ProductCatalogClient;
import com.jdp.checkout.bff.service.PreCheckoutService;
import com.jdp.checkout.bff.exception.BusinessException;
import com.jdp.checkout.bff.exception.ErrorCode;
import com.jdp.checkout.bff.exception.ExternalServiceException;
import com.jdp.checkout.bff.entity.Cart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.jdp.checkout.bff.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PreCheckoutServiceTest {

    @Mock ProductCatalogClient catalog;
    @Mock PaymentMethodsClient paymentMethods;
    @Mock CartRepository carts;

    PreCheckoutService service;

    @BeforeEach
    void setUp() {
        service = new PreCheckoutService(catalog, paymentMethods, carts, CLOCK);
    }

    @Test
    void createsCartWithProductsAndPaymentMethodsFromTheExternalServices() {
        when(catalog.findAll()).thenReturn(CATALOG);
        when(paymentMethods.findAvailable()).thenReturn(METHODS);
        when(carts.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Cart cart = service.preCheckout(List.of("Premium ", "premium"));

        assertThat(cart.id()).isNotNull();
        assertThat(cart.products()).containsExactly(PREMIUM);
        assertThat(cart.paymentMethods()).containsExactly(CREDIT, DEBIT, PIX);
        verify(carts).save(cart);
    }

    @Test
    void unknownProductIsRejectedAndNoCartIsCreated() {
        when(catalog.findAll()).thenReturn(CATALOG);

        assertThatThrownBy(() -> service.preCheckout(List.of("premium", "ultra")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ultra")
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
        verify(carts, never()).save(any());
    }

    @Test
    void noEnabledPaymentMethodIsAnExternalFailureAndNoCartIsCreated() {
        when(catalog.findAll()).thenReturn(CATALOG);
        when(paymentMethods.findAvailable()).thenReturn(List.of());

        assertThatThrownBy(() -> service.preCheckout(List.of("premium")))
                .isInstanceOf(ExternalServiceException.class)
                .extracting(e -> ((ExternalServiceException) e).errorCode())
                .isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
        verify(carts, never()).save(any());
    }
}
