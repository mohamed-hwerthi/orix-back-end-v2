package com.foodsquad.FoodSquad.model.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "stock_lots", indexes = {
        @Index(name = "idx_lot_item_expiry", columnList = "menu_item_id, expiryDate")
})
@Data
public class StockLot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "menu_item_id", nullable = false)
    private MenuItem menuItem;

    @Column(length = 100)
    private String batchNumber;

    @Column(nullable = false)
    private Integer quantity = 0;

    @Column(nullable = false)
    private Integer initialQuantity = 0;

    @Column(nullable = false)
    private LocalDate expiryDate;

    @Column(nullable = false)
    private LocalDate receivedDate = LocalDate.now();

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StockLotStatus status = StockLotStatus.ACTIVE;

    public enum StockLotStatus {
        ACTIVE, CONSUMED, EXPIRED, DAMAGED
    }
}
