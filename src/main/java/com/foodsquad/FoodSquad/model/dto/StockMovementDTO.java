package com.foodsquad.FoodSquad.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.foodsquad.FoodSquad.model.entity.StockMovementType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class StockMovementDTO {

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long id;

    @NotNull(message = "menuItemId is required")
    private Long menuItemId;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String menuItemTitle;

    @NotNull(message = "type is required")
    private StockMovementType type;

    @NotNull(message = "quantity is required")
    private Integer quantity;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Integer stockBefore;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Integer stockAfter;

    private String reason;

    private String referenceDoc;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String userEmail;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private LocalDateTime createdAt;
}
