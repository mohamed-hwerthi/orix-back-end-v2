package com.foodsquad.FoodSquad.controller;

import com.foodsquad.FoodSquad.model.dto.StockLotDTO;
import com.foodsquad.FoodSquad.service.impl.StockLotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stock-lots")
@Tag(name = "8. Stock Lots", description = "Lots & dates de péremption (FIFO)")
public class StockLotController {

    private final StockLotService stockLotService;

    public StockLotController(StockLotService stockLotService) {
        this.stockLotService = stockLotService;
    }

    @Operation(summary = "Créer un lot pour un article (réception)")
    @PostMapping
    public ResponseEntity<StockLotDTO> create(@Valid @RequestBody StockLotDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(stockLotService.createLot(dto));
    }

    @Operation(summary = "Lister les lots d'un article (toutes statuts)")
    @GetMapping("/by-item/{menuItemId}")
    public ResponseEntity<List<StockLotDTO>> byItem(@PathVariable Long menuItemId) {
        return ResponseEntity.ok(stockLotService.findByMenuItem(menuItemId));
    }

    @Operation(summary = "Lots actifs périmant dans les N prochains jours")
    @GetMapping("/expiring")
    public ResponseEntity<List<StockLotDTO>> expiring(@RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(stockLotService.findExpiring(days));
    }

    @Operation(summary = "Compteur lots à risque (pour widgets)")
    @GetMapping("/expiring/count")
    public ResponseEntity<Map<String, Long>> expiringCount(@RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(Map.of("count", stockLotService.countExpiring(days)));
    }

    @Operation(summary = "Marquer un lot comme expiré (decremente le stock global)")
    @PostMapping("/{id}/mark-expired")
    public ResponseEntity<StockLotDTO> markExpired(@PathVariable Long id) {
        return ResponseEntity.ok(stockLotService.markExpired(id));
    }

    @Operation(summary = "Supprimer un lot (rollback du stock global)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id) {
        stockLotService.deleteLot(id);
        return ResponseEntity.ok(Map.of("message", "Lot supprimé"));
    }
}
