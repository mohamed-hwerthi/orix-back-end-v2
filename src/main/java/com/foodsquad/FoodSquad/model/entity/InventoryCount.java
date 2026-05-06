package com.foodsquad.FoodSquad.model.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "inventory_counts", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"session_id", "menu_item_id"})
})
@Data
public class InventoryCount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private InventorySession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "menu_item_id", nullable = false)
    private MenuItem menuItem;

    @Column(nullable = false)
    private Integer expectedQuantity = 0;

    @Column(nullable = false)
    private Integer countedQuantity = 0;

    public int delta() {
        return countedQuantity - expectedQuantity;
    }
}
