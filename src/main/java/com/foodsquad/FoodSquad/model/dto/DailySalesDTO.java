package com.foodsquad.FoodSquad.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailySalesDTO {
    private LocalDate day;
    private long ordersCount;
    private double revenue;
}
