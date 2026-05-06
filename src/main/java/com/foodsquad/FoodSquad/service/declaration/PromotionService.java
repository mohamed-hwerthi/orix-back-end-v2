package com.foodsquad.FoodSquad.service.declaration;

import com.foodsquad.FoodSquad.model.dto.PromotionDTO;

import java.util.List;

public interface PromotionService {

    PromotionDTO create(PromotionDTO dto);

    PromotionDTO update(Long id, PromotionDTO dto);

    void delete(Long id);

    PromotionDTO getById(Long id);

    List<PromotionDTO> getAll();

    List<PromotionDTO> getActive();

    PromotionDTO validateCode(String code);

    com.foodsquad.FoodSquad.model.dto.PromotionStatsDTO getStats(Long id);

    java.util.List<com.foodsquad.FoodSquad.model.dto.PromotionStatsDTO> getAllStats();

    java.util.List<com.foodsquad.FoodSquad.model.dto.PromotionStatsDTO.OrderRow> getOrdersUsing(Long id);
}
