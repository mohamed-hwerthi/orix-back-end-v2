package com.foodsquad.FoodSquad.controller;

import com.foodsquad.FoodSquad.model.dto.InventoryCountDTO;
import com.foodsquad.FoodSquad.model.dto.InventorySessionDTO;
import com.foodsquad.FoodSquad.service.impl.InventorySessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory-sessions")
@Tag(name = "9. Inventory Sessions", description = "Sessions d'inventaire physique")
public class InventorySessionController {

    private final InventorySessionService inventoryService;

    public InventorySessionController(InventorySessionService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @Operation(summary = "Lister toutes les sessions d'inventaire")
    @GetMapping
    public ResponseEntity<List<InventorySessionDTO>> getAll() {
        return ResponseEntity.ok(inventoryService.findAll());
    }

    @Operation(summary = "Détail d'une session avec tous les comptages")
    @GetMapping("/{id}")
    public ResponseEntity<InventorySessionDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(inventoryService.findById(id));
    }

    @Operation(summary = "Démarrer une nouvelle session (snapshot du stock courant)")
    @PostMapping
    public ResponseEntity<InventorySessionDTO> create(@Valid @RequestBody InventorySessionDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(inventoryService.create(dto));
    }

    @Operation(summary = "Mettre à jour les quantités comptées (DRAFT only)")
    @PutMapping("/{id}/counts")
    public ResponseEntity<InventorySessionDTO> updateCounts(@PathVariable Long id,
                                                            @RequestBody List<InventoryCountDTO> counts) {
        return ResponseEntity.ok(inventoryService.updateCounts(id, counts));
    }

    @Operation(summary = "Valider la session — génère les ADJUSTMENT movements")
    @PostMapping("/{id}/commit")
    public ResponseEntity<InventorySessionDTO> commit(@PathVariable Long id) {
        return ResponseEntity.ok(inventoryService.commit(id));
    }

    @Operation(summary = "Annuler une session DRAFT")
    @PostMapping("/{id}/cancel")
    public ResponseEntity<InventorySessionDTO> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(inventoryService.cancel(id));
    }
}
