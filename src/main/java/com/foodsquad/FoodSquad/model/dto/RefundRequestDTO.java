package com.foodsquad.FoodSquad.model.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.List;

@Data
public class RefundRequestDTO {

    @NotEmpty(message = "At least one item must be refunded")
    private List<RefundLine> items;

    /** Reason: GENERIC | DAMAGED | EXPIRED | CUSTOMER_ERROR — DAMAGED/EXPIRED won't restock */
    private String reason = "GENERIC";

    private String notes;

    @Data
    public static class RefundLine {
        @NotNull
        private Long menuItemId;
        @NotNull
        @Positive
        private Integer quantity;
    }
}
