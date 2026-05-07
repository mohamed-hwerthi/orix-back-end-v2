package com.foodsquad.FoodSquad.model.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class SalesStatsDTO {
    private LocalDate from;
    private LocalDate to;
    private double totalRevenue;
    private long totalOrders;
    private long totalItems;
    private double averageTicket;
    private List<DailySalesDTO> daily;
}
