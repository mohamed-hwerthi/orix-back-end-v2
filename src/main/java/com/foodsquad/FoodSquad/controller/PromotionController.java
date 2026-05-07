package com.foodsquad.FoodSquad.controller;

import com.foodsquad.FoodSquad.model.dto.PromotionDTO;
import com.foodsquad.FoodSquad.service.declaration.PromotionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/promotions")
@Tag(name = "7. Promotions", description = "Gestion des promotions")
public class PromotionController {

    private final PromotionService promotionService;

    public PromotionController(PromotionService promotionService) {
        this.promotionService = promotionService;
    }

    @Operation(summary = "Liste de toutes les promotions")
    @GetMapping
    public ResponseEntity<List<PromotionDTO>> getAll() {
        return ResponseEntity.ok(promotionService.getAll());
    }

    @Operation(summary = "Liste des promotions actives à la date du jour")
    @GetMapping("/active")
    public ResponseEntity<List<PromotionDTO>> getActive() {
        return ResponseEntity.ok(promotionService.getActive());
    }

    @Operation(summary = "Valider un code promo (404 si invalide/expiré/épuisé)")
    @GetMapping("/validate-code/{code}")
    public ResponseEntity<PromotionDTO> validateCode(@PathVariable String code) {
        return ResponseEntity.ok(promotionService.validateCode(code));
    }

    @Operation(summary = "Statistiques d'utilisation par promotion")
    @GetMapping("/stats")
    public ResponseEntity<List<com.foodsquad.FoodSquad.model.dto.PromotionStatsDTO>> getAllStats() {
        return ResponseEntity.ok(promotionService.getAllStats());
    }

    @Operation(summary = "Statistiques détaillées d'une promotion (avec top produits)")
    @GetMapping("/{id}/stats")
    public ResponseEntity<com.foodsquad.FoodSquad.model.dto.PromotionStatsDTO> getStats(@PathVariable Long id) {
        return ResponseEntity.ok(promotionService.getStats(id));
    }

    @Operation(summary = "Liste des commandes ayant utilisé cette promotion")
    @GetMapping("/{id}/orders")
    public ResponseEntity<List<com.foodsquad.FoodSquad.model.dto.PromotionStatsDTO.OrderRow>> getOrdersUsing(@PathVariable Long id) {
        return ResponseEntity.ok(promotionService.getOrdersUsing(id));
    }

    @Operation(summary = "Récupérer une promotion par ID")
    @GetMapping("/{id}")
    public ResponseEntity<PromotionDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(promotionService.getById(id));
    }

    @Operation(summary = "Créer une promotion")
    @PostMapping
    public ResponseEntity<PromotionDTO> create(@Valid @RequestBody PromotionDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(promotionService.create(dto));
    }

    @Operation(summary = "Mettre à jour une promotion")
    @PutMapping("/{id}")
    public ResponseEntity<PromotionDTO> update(@PathVariable Long id, @Valid @RequestBody PromotionDTO dto) {
        return ResponseEntity.ok(promotionService.update(id, dto));
    }

    @Operation(summary = "Supprimer une promotion")
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id) {
        promotionService.delete(id);
        return ResponseEntity.ok(Map.of("message", "Promotion supprimée"));
    }
}
