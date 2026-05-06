package com.foodsquad.FoodSquad.controller;

import com.foodsquad.FoodSquad.model.dto.SalesStatsDTO;
import com.foodsquad.FoodSquad.model.dto.TopProductDTO;
import com.foodsquad.FoodSquad.service.declaration.SalesStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/sales-stats")
@Tag(name = "8. Sales Stats", description = "Statistiques et exports des ventes")
public class SalesStatsController {

    private final SalesStatsService salesStatsService;

    public SalesStatsController(SalesStatsService salesStatsService) {
        this.salesStatsService = salesStatsService;
    }

    @Operation(summary = "Statistiques agrégées par jour sur une période")
    @GetMapping
    public ResponseEntity<SalesStatsDTO> getStats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(salesStatsService.getStats(from, to));
    }

    @Operation(summary = "Top N produits sur une période")
    @GetMapping("/top-products")
    public ResponseEntity<List<TopProductDTO>> getTopProducts(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(salesStatsService.getTopProducts(from, to, limit));
    }

    @Operation(summary = "Export CSV des ventes sur une période")
    @GetMapping(value = "/export.csv", produces = "text/csv")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        String csv = salesStatsService.exportCsv(from, to);
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);
        String filename = "ventes_" + from + "_" + to + ".csv";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDispositionFormData("attachment", filename);
        headers.setContentLength(bytes.length);
        return new ResponseEntity<>(bytes, headers, 200);
    }
}
