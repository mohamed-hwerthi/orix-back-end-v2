package com.foodsquad.FoodSquad.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TopProductDTO {
    private Long menuItemId;
    private String title;
    private long quantitySold;
    private double revenue;
}
