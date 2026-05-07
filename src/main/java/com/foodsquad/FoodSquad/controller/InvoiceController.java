package com.foodsquad.FoodSquad.controller;

import com.foodsquad.FoodSquad.service.declaration.InvoiceService;
import com.foodsquad.FoodSquad.service.declaration.InvoiceServiceImp;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Controller for managing menus in the Food Squad application.
 * Provides APIs for retrieving, creating, updating, and deleting menus.
 */
@Validated
@RestController
@RequestMapping("/api/invoice")
@Slf4j
@Tag(name = "9. Invoice Management", description = "Invoice Management API")
public class InvoiceController {
    private final InvoiceServiceImp invoiceService;

    /** Daemon thread pool for fire-and-forget physical printing — never blocks response. */
    private static final ExecutorService PRINT_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "thermal-printer-async");
        t.setDaemon(true);
        return t;
    });

    public InvoiceController(InvoiceService invoiceServiceImp, InvoiceServiceImp invoiceService) {

        this.invoiceService = invoiceService;
    }

//    @Operation(summary = "Generate Order Invoice", description = "Generates and returns an invoice for the given order ID and locale.")
//    @PostMapping("/generate")
//    public ResponseEntity<Resource> generateOrderInvoice(
//            @Parameter(description = "ID of the order", required = true) @RequestParam(name = "orderId") String orderId,
//            @Parameter(description = "Locale key for invoice generation", required = true) @RequestParam Locale localeKey) {
//        File pdfFile = invoiceService.generateOrderInvoice(orderId, localeKey);
//        if (pdfFile == null || !pdfFile.exists()) {
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
//        }
//        Resource resource = new FileSystemResource(pdfFile);
//        HttpHeaders headers = new HttpHeaders();
//        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=invoice_" + orderId + ".pdf");
//
//        return ResponseEntity.ok()
//                .headers(headers)
//                .contentLength(pdfFile.length())
//                .contentType(MediaType.APPLICATION_PDF)
//                .body(resource);
//    }

    /**
     *
     * author ismail benkraiem
     * @param orderId
     * @return
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<byte[]> downloadInvoice(@PathVariable String orderId) {
        try {
            long t0 = System.currentTimeMillis();
            byte[] pdfBytes = invoiceService.generateInvoice(orderId);
            long t1 = System.currentTimeMillis();
            log.info("Invoice {} generated in {} ms ({} bytes)", orderId, (t1 - t0), pdfBytes.length);

            // Fire-and-forget: physical printing happens off-thread so it NEVER blocks the response.
            // The browser will receive the PDF immediately; the thermal printer (if any) prints in parallel.
            String fileName = "invoice_" + orderId + ".pdf";
            PRINT_EXECUTOR.submit(() -> tryPhysicalPrint(orderId, pdfBytes, fileName));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("inline", fileName); // inline → browser preview faster than attachment
            headers.setCacheControl("no-cache, no-store, must-revalidate");
            return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);

        } catch (Exception e) {
            log.error("Error generating invoice", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /** Off-thread thermal printing. Failures here NEVER affect the API response. */
    private void tryPhysicalPrint(String orderId, byte[] pdfBytes, String fileName) {
        try {
            Path tempPath = Paths.get(System.getProperty("java.io.tmpdir"), fileName);
            Files.write(tempPath, pdfBytes);
            ProcessBuilder pb = new ProcessBuilder("lp", "-d", "EPSON_TM-T20X", tempPath.toString());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            // Bound the wait so a hung printer never accumulates threads
            boolean finished = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("Thermal print {} killed after timeout", orderId);
            } else if (process.exitValue() != 0) {
                log.warn("Thermal print {} exit={}", orderId, process.exitValue());
            }
        } catch (Exception e) {
            // Most common: no printer attached → just log at debug level
            log.debug("Thermal print skipped for {}: {}", orderId, e.getMessage());
        }
    }
}
