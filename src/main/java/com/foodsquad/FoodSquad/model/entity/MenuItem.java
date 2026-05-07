package com.foodsquad.FoodSquad.model.entity;

import com.foodsquad.FoodSquad.model.Menu;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "menu_items")
@Data
@EqualsAndHashCode(of = "id")
public class MenuItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String description;


    @Column(nullable = false)
    private Double price = 1.0;

    @Column(nullable = true, name = "codeBar", unique = true)
    private String barCode;

    @Column(nullable = true, unique = true)
    private String sku;

    @Column(nullable = true)
    private Double purchasePrice;

    @Column(nullable = false)
    private Integer stockQuantity = 0;

    @Column(nullable = false)
    private Integer minStockAlert = 0;

    @Column(nullable = true, length = 20)
    private String unit;

    @Column(nullable = false)
    private Boolean isActive = true;

    @Column(nullable = false)
    private Boolean allowNegativeStock = false;

    @Column
    private Integer reorderQty;

    @Column(nullable = false)
    private Boolean hasExpiryDate = false;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;


    @OneToMany(mappedBy = "menuItem", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Review> reviews = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdOn;

    @ManyToMany(mappedBy = "menuItems")
    private List<Menu> menus;
    @ManyToOne
    @JoinColumn(name = "currency_id", nullable = false)
    private Currency currency;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "tax_id", referencedColumnName = "id")
    private Tax tax;
    @ManyToMany
    @JoinTable(
            name = "menu_item_categories",
            joinColumns = @JoinColumn(name = "menu_item_id"),

            inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    private List<Category> categories = new ArrayList<>();

    @ManyToMany
    @JoinTable(
            name = "menu_item_medias",
            joinColumns = @JoinColumn(name = "menu_item_id"),
            inverseJoinColumns = @JoinColumn(name = "media_id")
    )
    private List<Media> medias = new ArrayList<>();

    @PrePersist
    protected void onCreate() {

        this.createdOn = LocalDateTime.now();
    }


}
