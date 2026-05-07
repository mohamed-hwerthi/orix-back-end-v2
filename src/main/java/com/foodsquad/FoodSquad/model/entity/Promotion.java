package com.foodsquad.FoodSquad.model.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "promotions")
@Data
public class Promotion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PromotionType type;

    @Column(nullable = false)
    private Double value;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Column(nullable = false)
    private Boolean isActive = true;

    @Column(unique = true, length = 50)
    private String promoCode;

    @Column
    private Integer maxUses;

    @Column(nullable = false)
    private Integer usesCount = 0;

    @Column
    private Double minOrderAmount;

    @Column
    private Double maxDiscountAmount;

    @Column(nullable = false)
    private Boolean firstOrderOnly = false;

    @Column(nullable = false)
    private Boolean oncePerUser = false;

    @ElementCollection
    @CollectionTable(name = "promotion_used_by_users", joinColumns = @JoinColumn(name = "promotion_id"))
    @Column(name = "user_id")
    private java.util.Set<String> usedByUserIds = new java.util.HashSet<>();

    @ManyToMany
    @JoinTable(
            name = "promotion_menu_items",
            joinColumns = @JoinColumn(name = "promotion_id"),
            inverseJoinColumns = @JoinColumn(name = "menu_item_id")
    )
    private List<MenuItem> menuItems = new ArrayList<>();

    @ManyToMany
    @JoinTable(
            name = "promotion_categories",
            joinColumns = @JoinColumn(name = "promotion_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    private List<Category> categories = new ArrayList<>();
}
