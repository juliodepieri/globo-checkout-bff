package com.jdp.checkout.bff.controller;

import com.jdp.checkout.bff.dto.request.CheckoutRequest;
import com.jdp.checkout.bff.dto.request.PreCheckoutRequest;
import com.jdp.checkout.bff.dto.response.CheckoutResponse;
import com.jdp.checkout.bff.dto.response.PreCheckoutResponse;
import com.jdp.checkout.bff.mapper.CheckoutMapper;
import com.jdp.checkout.bff.service.CheckoutService;
import com.jdp.checkout.bff.service.PreCheckoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/operations")
@Tag(name = "Checkout", description = "Subscription checkout: pre-checkout, then checkout")
public class OperationsController {

    public static final String PRE_CHECKOUT = "preCheckout";
    public static final String CHECKOUT = "checkout";

    private final PreCheckoutService preCheckoutService;
    private final CheckoutService checkoutService;

    public OperationsController(PreCheckoutService preCheckoutService, CheckoutService checkoutService) {
        this.preCheckoutService = preCheckoutService;
        this.checkoutService = checkoutService;
    }

    @Operation(operationId = PRE_CHECKOUT, summary = "Create the cart for the payment screen",
            description = "Validates the chosen plans against the products service and returns them with the "
                    + "payment methods available. Prices and methods are frozen in the cart.")
    @ApiResponse(responseCode = "200", description = "Cart created")
    @PostMapping("/pre-checkout")
    public PreCheckoutResponse preCheckout(@Valid @RequestBody PreCheckoutRequest request) {
        var slugs = request.products().stream().map(PreCheckoutRequest.ProductRef::slug).toList();
        return CheckoutMapper.toResponse(preCheckoutService.preCheckout(slugs));
    }

    @Operation(operationId = CHECKOUT, summary = "Pay a cart",
            description = "Charges the cart with one of the methods offered by its pre-checkout. A declined cart "
                    + "can be paid again; an approved one cannot (409).")
    @ApiResponse(responseCode = "200", description = "Payment approved")
    @PostMapping("/checkout")
    public CheckoutResponse checkout(@Valid @RequestBody CheckoutRequest request) {
        return CheckoutMapper.toResponse(checkoutService.checkout(CheckoutMapper.toCommand(request)));
    }
}
