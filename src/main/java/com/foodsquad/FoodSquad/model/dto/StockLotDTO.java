package com.foodsquad.FoodSquad.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class StockLotDTO {

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long id;

    @NotNull(message = "menuItemId is required")
    private Long menuItemId;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String menuItemTitle;

    private String batchNumber;

    @NotNull
    @Positive(message = "Quantity must be positive")
    private Integer quantity;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Integer initialQuantity;

    @NotNull(message = "Expiry date is required")
    private LocalDate expiryDate;

    private LocalDate receivedDate;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private LocalDateTime createdAt;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String status;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Integer daysUntilExpiry;
}
