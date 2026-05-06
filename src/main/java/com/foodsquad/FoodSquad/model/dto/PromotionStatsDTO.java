package com.foodsquad.FoodSquad.model.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PromotionStatsDTO {

    private Long promotionId;
    private String promotionName;
    private int ordersCount;
    private double revenue;
    private double discountTotal;
    private double originalTotal;
    private double avgDiscountPerOrder;
    private List<TopProduct> topProducts = new ArrayList<>();

    @Data
    public static class TopProduct {
        private Long menuItemId;
        private String title;
        private long quantitySold;

        public TopProduct() {}

        public TopProduct(Long menuItemId, String title, long quantitySold) {
            this.menuItemId = menuItemId;
            this.title = title;
            this.quantitySold = quantitySold;
        }
    }

    @Data
    public static class OrderRow {
        private String orderId;
        private String createdOn;
        private double totalCost;
        private double originalAmount;
        private double discountAmount;
        private String status;
        private String userEmail;
    }
}
