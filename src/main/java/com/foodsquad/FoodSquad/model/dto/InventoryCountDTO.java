package com.foodsquad.FoodSquad.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class InventoryCountDTO {

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long id;

    @NotNull
    private Long menuItemId;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String menuItemTitle;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String sku;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Integer expectedQuantity;

    @NotNull
    private Integer countedQuantity;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Integer delta;
}
