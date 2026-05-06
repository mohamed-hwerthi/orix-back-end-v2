package com.foodsquad.FoodSquad.controller;

import com.foodsquad.FoodSquad.model.dto.CashMovementDTO;
import com.foodsquad.FoodSquad.model.dto.CashSessionDTO;
import com.foodsquad.FoodSquad.model.dto.CashSessionSummaryDTO;
import com.foodsquad.FoodSquad.service.impl.CashSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cash-sessions")
@Tag(name = "10. Cash Sessions", description = "Sessions caisse, clôtures Z-report")
public class CashSessionController {

    private final CashSessionService service;

    public CashSessionController(CashSessionService service) {
        this.service = service;
    }

    @Operation(summary = "Ouvrir une session de caisse (déclare le fond initial)")
    @PostMapping("/open")
    public ResponseEntity<CashSessionDTO> open(@Valid @RequestBody CashSessionDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.open(dto));
    }

    @Operation(summary = "Session ouverte du caissier connecté (404 si aucune)")
    @GetMapping("/current")
    public ResponseEntity<CashSessionDTO> current() {
        return ResponseEntity.ok(service.getCurrent());
    }

    @Operation(summary = "Liste de toutes les sessions (admin)")
    @GetMapping
    public ResponseEntity<List<CashSessionDTO>> all() {
        return ResponseEntity.ok(service.findAll());
    }

    @Operation(summary = "Détails d'une session")
    @GetMapping("/{id}")
    public ResponseEntity<CashSessionDTO> byId(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @Operation(summary = "Récap théorique (CA, paiements, mouvements, espèces théoriques)")
    @GetMapping("/{id}/summary")
    public ResponseEntity<CashSessionSummaryDTO> summary(@PathVariable Long id) {
        return ResponseEntity.ok(service.summary(id));
    }

    @Operation(summary = "Clôturer une session (irrévocable)")
    @PostMapping("/{id}/close")
    public ResponseEntity<CashSessionDTO> close(@PathVariable Long id, @RequestBody CashSessionDTO dto) {
        return ResponseEntity.ok(service.close(id, dto));
    }

    @Operation(summary = "Ajouter un mouvement de caisse manuel (in/out)")
    @PostMapping("/{id}/movements")
    public ResponseEntity<CashMovementDTO> addMovement(@PathVariable Long id,
                                                       @Valid @RequestBody CashMovementDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addMovement(id, dto));
    }

    @Operation(summary = "Liste les mouvements d'une session")
    @GetMapping("/{id}/movements")
    public ResponseEntity<List<CashMovementDTO>> listMovements(@PathVariable Long id) {
        return ResponseEntity.ok(service.listMovements(id));
    }

    @org.springframework.beans.factory.annotation.Autowired
    private com.foodsquad.FoodSquad.service.impl.ZReportService zReportService;

    @Operation(summary = "Télécharger le Z-report PDF d'une session clôturée")
    @GetMapping(value = "/{id}/z-report.pdf", produces = "application/pdf")
    public ResponseEntity<byte[]> zReport(@PathVariable Long id) throws net.sf.jasperreports.engine.JRException {
        byte[] pdf = zReportService.generateZReport(id);
        return ResponseEntity.ok()
                .header("Content-Disposition", "inline; filename=z-report-" + id + ".pdf")
                .body(pdf);
    }
}
