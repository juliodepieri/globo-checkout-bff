package com.jdp.checkout.bff.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "Plans the user chose on the plans screen (1 to 10; duplicates are ignored)")
public record PreCheckoutRequest(@NotEmpty @Size(max = 10) List<@Valid @NotNull ProductRef> products) {

    public record ProductRef(@NotBlank String slug) {
    }
}
