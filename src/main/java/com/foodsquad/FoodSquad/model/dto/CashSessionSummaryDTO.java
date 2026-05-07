package com.foodsquad.FoodSquad.model.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class CashSessionSummaryDTO {

    private Long sessionId;
    private LocalDateTime openedAt;
    private LocalDateTime asOf;
    private Double openingAmount;

    // Sales
    private int ordersCount;
    private int unitsSold;
    private double totalRevenue;
    private double totalDiscount;
    private double totalOriginal;

    // By payment method
    private double cashSales;
    private double cardSales;
    private double mixedSales;
    private double otherSales;

    // Refunds
    private int refundsCount;
    private double refundsAmount;

    // Manual cash movements
    private double cashIn;
    private double cashOut;

    // Theoretical balances
    private double expectedCashInDrawer;   // openingAmount + cashSales + cashIn − cashOut − cashRefunds
    private double expectedCardTotal;      // cardSales

    private List<TopProduct> topProducts = new ArrayList<>();

    @Data
    public static class TopProduct {
        public Long menuItemId;
        public String title;
        public long quantitySold;
        public double revenue;
    }
}
