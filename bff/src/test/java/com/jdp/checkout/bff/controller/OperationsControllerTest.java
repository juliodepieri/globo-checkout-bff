package com.jdp.checkout.bff.controller;

import com.jdp.checkout.bff.exception.FrontendErrorResolver;
import com.jdp.checkout.bff.service.CheckoutService;
import com.jdp.checkout.bff.service.CheckoutService.CheckoutCommand;
import com.jdp.checkout.bff.service.CheckoutService.CheckoutResult;
import com.jdp.checkout.bff.service.PreCheckoutService;
import com.jdp.checkout.bff.exception.BusinessException;
import com.jdp.checkout.bff.exception.ErrorCode;
import com.jdp.checkout.bff.exception.PaymentDeclinedException;
import com.jdp.checkout.bff.entity.Cart;
import com.jdp.checkout.bff.model.ChargeResult;
import com.jdp.checkout.bff.entity.Payment;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static com.jdp.checkout.bff.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP contract of the BFF: JSON shapes for success and for every kind of error. */
@WebMvcTest(OperationsController.class)
@Import(FrontendErrorResolver.class)
class OperationsControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean PreCheckoutService preCheckout;
    @MockitoBean CheckoutService checkout;

    private ResultActions postJson(String path, String body) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void preCheckoutReturnsCartReadyForThePaymentScreen() throws Exception {
        Cart cart = premiumCart();
        when(preCheckout.preCheckout(List.of("premium"))).thenReturn(cart);

        postJson("/operations/pre-checkout", """
                {"products":[{"slug":"premium"}]}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cartId").value(cart.id().toString()))
                .andExpect(jsonPath("$.products[0].slug").value("premium"))
                .andExpect(jsonPath("$.products[0].name").value("Plano Premium"))
                .andExpect(jsonPath("$.products[0].description").value("Assinatura mensal com catálogo completo"))
                .andExpect(jsonPath("$.products[0].price").value(39.90))
                .andExpect(jsonPath("$.products[0].currency").value("BRL"))
                .andExpect(jsonPath("$.paymentMethods[*].name").value(
                        org.hamcrest.Matchers.contains("Cartão de Crédito", "Cartão de Débito", "Pix")));
    }

    @Test
    void preCheckoutWithUnknownProductReturnsFrontendError() throws Exception {
        when(preCheckout.preCheckout(any()))
                .thenThrow(new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "Unknown products: [ultra]"));

        postJson("/operations/pre-checkout", """
                {"products":[{"slug":"ultra"}]}""")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.displayType").value("BLOCK"))
                .andExpect(jsonPath("$.errorCode").value("PRODUCT_NOT_FOUND"))
                .andExpect(jsonPath("$.cartId").value(nullValue()))
                .andExpect(jsonPath("$.frontendError.code").value("P-1002"))
                .andExpect(jsonPath("$.frontendError.primaryButton.label").exists());
    }

    @Test
    void checkoutWithCardReturnsSuccessScreenData() throws Exception {
        Cart cart = premiumCart();
        cart.approve(NOW);
        var payment = Payment.of(cart, CREDIT, ChargeResult.approvedCard("4242"), NOW);
        when(checkout.checkout(any())).thenReturn(new CheckoutResult(cart, payment));

        postJson("/operations/checkout", """
                {"cartId":"%s","paymentMethod":{"name":"Cartão de Crédito",
                 "card":{"holderName":"Ana Souza","number":"4111111111114242","expiry":"12/28","cvv":"123"}}}
                """.formatted(cart.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cartId").value(cart.id().toString()))
                .andExpect(jsonPath("$.status").value("APROVADO"))
                .andExpect(jsonPath("$.paidAt").value("2026-10-14T16:00:00Z"))
                .andExpect(jsonPath("$.payment.method").value("Cartão de Crédito"))
                .andExpect(jsonPath("$.payment.lastDigits").value("4242"))
                .andExpect(jsonPath("$.payment.pixCode").doesNotExist())
                .andExpect(jsonPath("$.payment.amount").value(39.90))
                .andExpect(jsonPath("$.payment.currency").value("BRL"))
                .andExpect(jsonPath("$.products[0].slug").value("premium"))
                .andExpect(jsonPath("$.products[0].price").value(39.90));

        var command = ArgumentCaptor.forClass(CheckoutCommand.class);
        verify(checkout).checkout(command.capture());
        assertThat(command.getValue().card().number()).isEqualTo("4111111111114242");
    }

    @Test
    void checkoutWithPixReturnsPixCodeInsteadOfLastDigits() throws Exception {
        Cart cart = premiumCart();
        cart.approve(NOW);
        var payment = Payment.of(cart, PIX, ChargeResult.approvedPix("00020126PIXCODE"), NOW);
        when(checkout.checkout(any())).thenReturn(new CheckoutResult(cart, payment));

        postJson("/operations/checkout", """
                {"cartId":"%s","paymentMethod":{"name":"Pix"}}""".formatted(cart.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.method").value("Pix"))
                .andExpect(jsonPath("$.payment.pixCode").value("00020126PIXCODE"))
                .andExpect(jsonPath("$.payment.lastDigits").doesNotExist());
    }

    @Test
    void declinedPaymentReturnsTheSpecErrorObject() throws Exception {
        Cart cart = premiumCart();
        when(checkout.checkout(any())).thenThrow(new PaymentDeclinedException(cart.id(), "CARD_DECLINED_BY_ISSUER"));

        postJson("/operations/checkout", """
                {"cartId":"%s","paymentMethod":{"name":"Cartão de Crédito",
                 "card":{"holderName":"Ana Souza","number":"4111111111110002","expiry":"12/28","cvv":"123"}}}
                """.formatted(cart.id()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.displayType").value("BLOCK"))
                .andExpect(jsonPath("$.errorCode").value("PAYMENT_DECLINED"))
                .andExpect(jsonPath("$.cartId").value(cart.id().toString()))
                .andExpect(jsonPath("$.frontendError.code").value("P-1001"))
                .andExpect(jsonPath("$.frontendError.title").value("Pagamento não aprovado"))
                .andExpect(jsonPath("$.frontendError.description").value(
                        "Não foi possível concluir o pagamento. Tente novamente ou escolha outro meio."))
                .andExpect(jsonPath("$.frontendError.primaryButton.label").value("Tentar novamente"))
                .andExpect(jsonPath("$.frontendError.primaryButton.url").value("RETRY"))
                .andExpect(jsonPath("$.frontendError.secondaryButton.label").value("Trocar meio de pagamento"))
                .andExpect(jsonPath("$.frontendError.secondaryButton.url").value("/checkout/pagamento"));
    }

    @Test
    void errorTextsFollowAcceptLanguage() throws Exception {
        Cart cart = premiumCart();
        when(checkout.checkout(any())).thenThrow(new PaymentDeclinedException(cart.id(), "CARD_DECLINED_BY_ISSUER"));

        mvc.perform(post("/operations/checkout").contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9")
                        .content("""
                                {"cartId":"%s","paymentMethod":{"name":"Pix"}}""".formatted(cart.id())))
                .andExpect(status().isUnprocessableContent())
                .andExpect(header().string(HttpHeaders.VARY, org.hamcrest.Matchers.containsString("Accept-Language")))
                .andExpect(jsonPath("$.errorCode").value("PAYMENT_DECLINED"))
                .andExpect(jsonPath("$.frontendError.code").value("P-1001"))
                .andExpect(jsonPath("$.frontendError.title").value("Payment not approved"))
                .andExpect(jsonPath("$.frontendError.primaryButton.label").value("Try again"))
                .andExpect(jsonPath("$.frontendError.primaryButton.url").value("RETRY"));
    }

    @Test
    void duplicatePaymentReturnsConflict() throws Exception {
        Cart cart = premiumCart();
        when(checkout.checkout(any()))
                .thenThrow(new BusinessException(ErrorCode.CART_ALREADY_PAID, cart.id(), "already paid"));

        postJson("/operations/checkout", """
                {"cartId":"%s","paymentMethod":{"name":"Pix"}}""".formatted(cart.id()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CART_ALREADY_PAID"))
                .andExpect(jsonPath("$.frontendError.code").value("P-1006"));
    }

    @Test
    void beanValidationErrorsUseTheSameErrorObject() throws Exception {
        postJson("/operations/checkout", """
                {"cartId":"%s","paymentMethod":{"name":"Cartão de Crédito",
                 "card":{"holderName":"Ana","number":"12","expiry":"13/99","cvv":"x"}}}
                """.formatted(premiumCart().id()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.displayType").value("INTERVENTION"))
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.frontendError.code").value("P-1000"))
                .andExpect(jsonPath("$.frontendError.primaryButton").exists());
        verifyNoInteractions(checkout);
    }

    @Test
    void malformedJsonUsesTheSameErrorObject() throws Exception {
        postJson("/operations/pre-checkout", "{not json")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.frontendError.title").exists());
    }

    @Test
    void unexpectedErrorUsesTheSameErrorObject() throws Exception {
        when(preCheckout.preCheckout(any())).thenThrow(new IllegalStateException("boom"));

        postJson("/operations/pre-checkout", """
                {"products":[{"slug":"premium"}]}""")
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.frontendError.code").value("P-9999"));
    }
}
