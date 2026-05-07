package com.foodsquad.FoodSquad.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CashSessionDTO {

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long id;

    @NotNull
    @PositiveOrZero
    private Double openingAmount;

    private String openingNotes;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private LocalDateTime openedAt;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String openedByEmail;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private LocalDateTime closedAt;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String closedByEmail;

    private Double countedCash;
    private Double countedCard;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Double expectedCash;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Double expectedCard;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Double cashVariance;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Double cardVariance;

    private String closingNotes;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String zReportNumber;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String status;
}
