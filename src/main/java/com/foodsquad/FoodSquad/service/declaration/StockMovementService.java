package com.foodsquad.FoodSquad.service.declaration;

import com.foodsquad.FoodSquad.model.dto.PaginatedResponseDTO;
import com.foodsquad.FoodSquad.model.dto.StockMovementDTO;
import com.foodsquad.FoodSquad.model.entity.StockMovementType;

public interface StockMovementService {

    StockMovementDTO createMovement(StockMovementDTO dto);

    PaginatedResponseDTO<StockMovementDTO> getMovements(int page, int limit);

    PaginatedResponseDTO<StockMovementDTO> getMovementsByMenuItem(Long menuItemId, int page, int limit);

    void recordSaleMovement(Long menuItemId, int quantity, String orderRef);

    /** Returns stock to inventory (e.g. customer return, restockable). */
    void recordReturnMovement(Long menuItemId, int quantity, String orderRef, String reason);

    /** Disposes the returned items (e.g. damaged, expired). Stock global decreases like a SALE but typed LOSS. */
    void recordLossMovement(Long menuItemId, int quantity, String orderRef, String reason);
}
