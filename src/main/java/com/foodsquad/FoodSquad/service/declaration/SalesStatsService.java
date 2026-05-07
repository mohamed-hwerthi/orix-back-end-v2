package com.foodsquad.FoodSquad.service.declaration;

import com.foodsquad.FoodSquad.model.dto.SalesStatsDTO;
import com.foodsquad.FoodSquad.model.dto.TopProductDTO;

import java.time.LocalDate;
import java.util.List;

public interface SalesStatsService {

    SalesStatsDTO getStats(LocalDate from, LocalDate to);

    List<TopProductDTO> getTopProducts(LocalDate from, LocalDate to, int limit);

    String exportCsv(LocalDate from, LocalDate to);
}
