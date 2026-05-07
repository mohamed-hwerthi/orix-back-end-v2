package com.foodsquad.FoodSquad.controller;

import com.foodsquad.FoodSquad.model.dto.OrderDTO;
import com.foodsquad.FoodSquad.service.impl.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/orders")
@Tag(name = "4. Order Management", description = "Order Management API")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @Operation(summary = "Create a new order", description = "Create a new order with the provided details.")
    @PostMapping
    public ResponseEntity<OrderDTO> createOrder(
            @Valid @RequestBody OrderDTO orderDTO) {
        return orderService.createOrder(orderDTO);
    }

    @Operation(summary = "Get an order by ID", description = "Retrieve an order by its unique ID.")
    @GetMapping("/{id}")
    public ResponseEntity<OrderDTO> getOrderById(
            @Parameter(description = "ID of the order to retrieve", example = "1")
            @PathVariable String id) {
        return orderService.getOrderById(id);
    }

    @Operation(summary = "Get orders by user ID", description = "Retrieve a list of orders for a specific user by their unique user ID.")
    @GetMapping("/user/{userId}")
    public List<OrderDTO> getOrdersByUserId(
            @Parameter(description = "ID of the user whose orders to retrieve", example = "1")
            @PathVariable String userId,

            @Parameter(description = "Page number, starting from 0", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Number of items per page", example = "10")
            @RequestParam(defaultValue = "10") int size) {
        return orderService.getOrdersByUserId(userId, page, size);
    }

    @Operation(summary = "Get all orders", description = "Retrieve a list of all orders with pagination.")
    @GetMapping
    public List<OrderDTO> getAllOrders(
            @Parameter(description = "Page number, starting from 0", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Number of items per page", example = "10")
            @RequestParam(defaultValue = "10") int size) {
        return orderService.getAllOrders(page, size);
    }

    @Operation(summary = "Update an order by ID", description = "Update the details of an existing order by its unique ID.")
    @PutMapping("/{id}")
    public ResponseEntity<OrderDTO> updateOrder(
            @Parameter(description = "ID of the order to update", example = "1")
            @PathVariable String id,

            @Valid @RequestBody OrderDTO orderDTO) {
        return orderService.updateOrder(id, orderDTO);
    }

    @Operation(summary = "Rembourser une commande (total ou partiel) avec gestion stock")
    @PostMapping("/{id}/refund")
    public ResponseEntity<OrderDTO> refundOrder(
            @PathVariable String id,
            @Valid @RequestBody com.foodsquad.FoodSquad.model.dto.RefundRequestDTO request) {
        return orderService.refundOrder(id, request);
    }

    @Operation(summary = "Delete an order by ID", description = "Delete an existing order by its unique ID.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteOrder(
            @Parameter(description = "ID of the order to delete", example = "1")
            @PathVariable String id) {
        return orderService.deleteOrder(id);
    }

    @Operation(summary = "Delete orders by IDs", description = "Delete existing orders by their unique IDs.")
    @DeleteMapping("/batch")
    public ResponseEntity<Map<String, String>> deleteMenuItemsByIds(
            @Parameter(description = "List of IDs of the orders to delete", example = "[1, 2, 3]")
            @RequestParam List<String> ids) {
        return orderService.deleteOrders(ids);
    }
}
