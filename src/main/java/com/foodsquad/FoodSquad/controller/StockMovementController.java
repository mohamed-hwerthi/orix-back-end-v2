package com.foodsquad.FoodSquad.controller;

import com.foodsquad.FoodSquad.model.dto.PaginatedResponseDTO;
import com.foodsquad.FoodSquad.model.dto.StockMovementDTO;
import com.foodsquad.FoodSquad.service.declaration.StockMovementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/stock-movements")
@Tag(name = "6. Stock Movements", description = "Gestion des mouvements de stock")
public class StockMovementController {

    private final StockMovementService stockMovementService;

    public StockMovementController(StockMovementService stockMovementService) {
        this.stockMovementService = stockMovementService;
    }

    @Operation(summary = "Créer un mouvement de stock (IN / OUT / ADJUSTMENT / LOSS)")
    @PostMapping
    public ResponseEntity<StockMovementDTO> create(@Valid @RequestBody StockMovementDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(stockMovementService.createMovement(dto));
    }

    @Operation(summary = "Liste paginée de tous les mouvements")
    @GetMapping
    public ResponseEntity<PaginatedResponseDTO<StockMovementDTO>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(stockMovementService.getMovements(page, limit));
    }

    @Operation(summary = "Historique des mouvements pour un produit")
    @GetMapping("/by-menu-item/{menuItemId}")
    public ResponseEntity<PaginatedResponseDTO<StockMovementDTO>> byMenuItem(
            @PathVariable Long menuItemId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(stockMovementService.getMovementsByMenuItem(menuItemId, page, limit));
    }
}
