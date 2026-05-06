package com.foodsquad.FoodSquad.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.foodsquad.FoodSquad.model.entity.MenuItem;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class MenuItemDTO {

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long id;

    private String title;

    private String description;
    private String barCode;
    private String sku;
    @Positive(message = "Price must be positive")
    private Double price;
    private Double purchasePrice;
    private Integer stockQuantity;
    private Integer minStockAlert;
    private String unit;
    private Boolean isActive;
    private Boolean allowNegativeStock;
    private Integer reorderQty;
    private Boolean hasExpiryDate;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Boolean lowStock;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Integer salesCount;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long reviewCount;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Double averageRating;
    private List<CategoryDTO>categories = new ArrayList<>() ;
    private List<MediaDTO>medias = new ArrayList<>() ;



    private CurrencyDTO currency;

    private TaxDTO tax;


    public MenuItemDTO() {

    }

    private void copyStockFields(MenuItem menuItem) {
        this.sku = menuItem.getSku();
        this.purchasePrice = menuItem.getPurchasePrice();
        this.stockQuantity = menuItem.getStockQuantity();
        this.minStockAlert = menuItem.getMinStockAlert();
        this.unit = menuItem.getUnit();
        this.isActive = menuItem.getIsActive();
        this.allowNegativeStock = Boolean.TRUE.equals(menuItem.getAllowNegativeStock());
        this.reorderQty = menuItem.getReorderQty();
        this.hasExpiryDate = Boolean.TRUE.equals(menuItem.getHasExpiryDate());
        Integer qty = menuItem.getStockQuantity();
        Integer min = menuItem.getMinStockAlert();
        this.lowStock = (qty != null && min != null && qty <= min);
    }

    public MenuItemDTO(MenuItem menuItem, int salesCount, long reviewCount, double averageRating , List<CategoryDTO> categories ,  List < MediaDTO> mediaDTOS , CurrencyDTO currency) {

        this.id = menuItem.getId();
        this.title = menuItem.getTitle();
        this.medias = mediaDTOS  ;
        this.barCode = menuItem.getBarCode();
        this.description = menuItem.getDescription();
        this.categories = categories ;
        this.price = menuItem.getPrice();
        this.salesCount = salesCount;
        this.reviewCount = reviewCount;
        this.averageRating = averageRating;
        this.currency=currency;
        copyStockFields(menuItem);
    }
    public MenuItemDTO(MenuItem menuItem, int salesCount, long reviewCount, double averageRating , List<CategoryDTO> categories ,  List < MediaDTO> mediaDTOS ) {

        this.id = menuItem.getId();
        this.title = menuItem.getTitle();
        this.medias = mediaDTOS  ;
        this.barCode = menuItem.getBarCode();
        this.description = menuItem.getDescription();
        this.categories = categories ;
        this.price = menuItem.getPrice();
        this.salesCount = salesCount;
        this.reviewCount = reviewCount;
        this.averageRating = averageRating;
        copyStockFields(menuItem);
    }
    public MenuItemDTO(MenuItem menuItem, int salesCount, long reviewCount, double averageRating , List<CategoryDTO> categories ,  List < MediaDTO> mediaDTOS,TaxDTO taxDTO ) {

        this.id = menuItem.getId();
        this.title = menuItem.getTitle();
        this.medias = mediaDTOS  ;
        this.barCode = menuItem.getBarCode();
        this.description = menuItem.getDescription();
        this.categories = categories ;
        this.price = menuItem.getPrice();
        this.salesCount = salesCount;
        this.reviewCount = reviewCount;
        this.averageRating = averageRating;
        this.tax=taxDTO;
        copyStockFields(menuItem);
    }
    public MenuItemDTO(MenuItem menuItem, int salesCount, long reviewCount, double averageRating , List<CategoryDTO> categories ,  List < MediaDTO> mediaDTOS,CurrencyDTO currency,TaxDTO taxDTO ) {

        this.id = menuItem.getId();
        this.title = menuItem.getTitle();
        this.medias = mediaDTOS  ;
        this.barCode = menuItem.getBarCode();
        this.description = menuItem.getDescription();
        this.categories = categories ;
        this.price = menuItem.getPrice();
        this.salesCount = salesCount;
        this.reviewCount = reviewCount;
        this.averageRating = averageRating;
        this.currency=currency;
        this.tax=taxDTO;
        copyStockFields(menuItem);
    }
}