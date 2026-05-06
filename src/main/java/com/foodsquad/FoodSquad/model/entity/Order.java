package com.foodsquad.FoodSquad.model.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "CHAR(36)")
    private String id;

    @Column(nullable = false)
    private Double totalCost;

    @Column(nullable = false)
    private Double originalAmount = 0.0;

    @Column(nullable = false)
    private Double discountAmount = 0.0;

    @ElementCollection
    @CollectionTable(name = "order_applied_promotions", joinColumns = @JoinColumn(name = "order_id"))
    @Column(name = "promotion_id")
    private Set<Long> appliedPromotionIds = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.PENDING;

    @Column(nullable = false)
    private LocalDateTime createdOn;

    @Column(nullable = false)
    private Boolean paid = false;

    @ElementCollection
    @CollectionTable(name = "order_menu_item", joinColumns = @JoinColumn(name = "order_id"))
    @MapKeyJoinColumn(name = "menu_item_id")
    @Column(name = "quantity")
    private Map<MenuItem, Integer> menuItemsWithQuantity;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cash_session_id")
    private CashSession cashSession;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private PaymentMethod paymentMethod;


    public String getId() {

        return id;
    }

    public void setId(String id) {

        this.id = id;
    }

    public Double getTotalCost() {

        return totalCost;
    }

    public void setTotalCost(Double totalCost) {

        this.totalCost = totalCost;
    }

    public OrderStatus getStatus() {

        return status;
    }

    public void setStatus(OrderStatus status) {

        this.status = status;
    }

    public LocalDateTime getCreatedOn() {

        return createdOn;
    }

    public void setCreatedOn(LocalDateTime createdOn) {

        this.createdOn = createdOn;
    }

    public Map<MenuItem, Integer> getMenuItemsWithQuantity() {

        return menuItemsWithQuantity;
    }

    public void setMenuItemsWithQuantity(Map<MenuItem, Integer> menuItemsWithQuantity) {

        this.menuItemsWithQuantity = menuItemsWithQuantity;
    }

    public User getUser() {

        return user;
    }

    public void setUser(User user) {

        this.user = user;
    }

    public Boolean getPaid() {

        return paid;
    }

    public void setPaid(Boolean paid) {

        this.paid = paid;
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

    public CashSession getCashSession() {
        return cashSession;
    }

    public void setCashSession(CashSession cashSession) {
        this.cashSession = cashSession;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

}
