package com.foodsquad.FoodSquad.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.foodsquad.FoodSquad.model.entity.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


public class OrderDTO {
    //allows reading while returning the object only, TODO: create additional OrderCreateDTO
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
//    @NotNull(message = "Order ID is required")
    private String id;

    @NotNull(message = "Menu item quantities are required")
    @Schema(example = "{\"1\": 1, \"2\": 2}")
    private Map<Long, Integer> menuItemQuantities;

    @NotNull(message = "Status is required")
    @Schema(example = "PENDING")
    private OrderStatus status;

    @Positive(message = "Total cost must be positive")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Double totalCost;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Double originalAmount;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Double discountAmount;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Set<Long> appliedPromotionIds = new HashSet<>();

    private String promoCode;

    @NotNull(message = "Creation date is required")
    private LocalDateTime createdOn;

    @Schema(defaultValue = "false")
    private Boolean paid = false;

    @Schema(example = "admin@example.com" )
    private String userEmail;

    private com.foodsquad.FoodSquad.model.entity.PaymentMethod paymentMethod;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long cashSessionId;

    // Public no-argument constructor
    public OrderDTO() {
    }

    // Constructor to create DTO from Order entity
    public OrderDTO(String id, String userEmail, Map<Long, Integer> menuItemQuantities, OrderStatus status, Double totalCost, LocalDateTime createdOn, Boolean paid) {
        this.id = id;
        this.userEmail = userEmail;
        this.menuItemQuantities = menuItemQuantities;
        this.status = status;
        this.totalCost = totalCost;
        this.createdOn = createdOn;
        this.paid = paid;
    }

    public String getId() {
        return id;
    }

    public void setId(String orderId) {
        this.id = orderId;
    }


    public Map<Long, Integer> getMenuItemQuantities() {
        return menuItemQuantities;
    }

    public void setMenuItemQuantities(Map<Long, Integer> menuItemQuantities) {
        this.menuItemQuantities = menuItemQuantities;
    }

    @JsonProperty("status")
    public String getStatus() {
        return status.name();
    }

    public void setStatus(@NotNull(message = "Status is required") OrderStatus status) {
        this.status = status;
    }

    public @Positive(message = "Total cost must be positive") Double getTotalCost() {
        return totalCost;
    }

    public void setTotalCost(@Positive(message = "Total cost must be positive") Double totalCost) {
        this.totalCost = totalCost;
    }

    public @NotNull(message = "Creation date is required") LocalDateTime getCreatedOn() {
        return createdOn;
    }

    public void setCreatedOn(@NotNull(message = "Creation date is required") LocalDateTime createdOn) {
        this.createdOn = createdOn;
    }

    public Boolean getPaid() {
        return paid;
    }

    public void setPaid(Boolean paid) {
        this.paid = paid;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public Double getOriginalAmount() {
        return originalAmount;
    }

    public void setOriginalAmount(Double originalAmount) {
        this.originalAmount = originalAmount;
    }

    public Double getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(Double discountAmount) {
        this.discountAmount = discountAmount;
    }

    public Set<Long> getAppliedPromotionIds() {
        return appliedPromotionIds;
    }

    public void setAppliedPromotionIds(Set<Long> appliedPromotionIds) {
        this.appliedPromotionIds = appliedPromotionIds;
    }

    public String getPromoCode() {
        return promoCode;
    }

    public void setPromoCode(String promoCode) {
        this.promoCode = promoCode;
    }

    public com.foodsquad.FoodSquad.model.entity.PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(com.foodsquad.FoodSquad.model.entity.PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public Long getCashSessionId() {
        return cashSessionId;
    }

    public void setCashSessionId(Long cashSessionId) {
        this.cashSessionId = cashSessionId;
    }
}
