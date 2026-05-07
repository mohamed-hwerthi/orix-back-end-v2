package com.foodsquad.FoodSquad.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.foodsquad.FoodSquad.model.entity.PromotionType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class PromotionDTO {

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long id;

    @NotBlank(message = "Name is required")
    @Size(max = 150, message = "Name must be at most 150 characters")
    private String name;

    @Size(max = 500, message = "Description must be at most 500 characters")
    private String description;

    @NotNull(message = "Type is required")
    private PromotionType type;

    @NotNull(message = "Value is required")
    @DecimalMin(value = "0.01", message = "Value must be greater than 0")
    private Double value;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    private LocalDate endDate;

    private Boolean isActive = true;

    @Size(max = 50, message = "Promo code must be at most 50 characters")
    private String promoCode;

    @DecimalMin(value = "1", message = "maxUses must be at least 1")
    private Integer maxUses;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Integer usesCount = 0;

    @DecimalMin(value = "0.01", message = "minOrderAmount must be greater than 0")
    private Double minOrderAmount;

    @DecimalMin(value = "0.01", message = "maxDiscountAmount must be greater than 0")
    private Double maxDiscountAmount;

    private Boolean firstOrderOnly = false;

    private Boolean oncePerUser = false;

    private List<Long> menuItemIds = new ArrayList<>();

    private List<Long> categoryIds = new ArrayList<>();

    @JsonIgnore
    @AssertTrue(message = "End date must be on or after start date")
    public boolean isDateRangeValid() {
        if (startDate == null || endDate == null) return true;
        return !endDate.isBefore(startDate);
    }

    @JsonIgnore
    @AssertTrue(message = "PERCENT value must be between 0 and 100")
    public boolean isPercentValueValid() {
        if (type != PromotionType.PERCENT || value == null) return true;
        return value > 0 && value <= 100;
    }
}
