package com.foodsquad.FoodSquad.service.impl;

import com.foodsquad.FoodSquad.model.dto.PaginatedResponseDTO;
import com.foodsquad.FoodSquad.model.dto.StockMovementDTO;
import com.foodsquad.FoodSquad.model.entity.MenuItem;
import com.foodsquad.FoodSquad.model.entity.StockMovement;
import com.foodsquad.FoodSquad.model.entity.StockMovementType;
import com.foodsquad.FoodSquad.model.entity.User;
import com.foodsquad.FoodSquad.repository.MenuItemRepository;
import com.foodsquad.FoodSquad.repository.StockMovementRepository;
import com.foodsquad.FoodSquad.repository.UserRepository;
import com.foodsquad.FoodSquad.service.declaration.StockMovementService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StockMovementServiceImpl implements StockMovementService {

    private final StockMovementRepository stockMovementRepository;
    private final MenuItemRepository menuItemRepository;
    private final UserRepository userRepository;
    private final StockLotService stockLotService;

    public StockMovementServiceImpl(StockMovementRepository stockMovementRepository,
                                    MenuItemRepository menuItemRepository,
                                    UserRepository userRepository,
                                    StockLotService stockLotService) {
        this.stockMovementRepository = stockMovementRepository;
        this.menuItemRepository = menuItemRepository;
        this.userRepository = userRepository;
        this.stockLotService = stockLotService;
    }

    @Override
    @Transactional
    public StockMovementDTO createMovement(StockMovementDTO dto) {

        MenuItem menuItem = menuItemRepository.findById(dto.getMenuItemId())
                .orElseThrow(() -> new EntityNotFoundException("MenuItem not found: " + dto.getMenuItemId()));

        int qty = dto.getQuantity() != null ? dto.getQuantity() : 0;
        if (qty <= 0 && dto.getType() != StockMovementType.ADJUSTMENT) {
            throw new IllegalArgumentException("Quantity must be positive (use ADJUSTMENT for corrections)");
        }

        int before = menuItem.getStockQuantity() != null ? menuItem.getStockQuantity() : 0;
        int after = computeNewStock(before, qty, dto.getType());

        if (after < 0) {
            throw new IllegalArgumentException("Resulting stock cannot be negative (current=" + before + ")");
        }

        menuItem.setStockQuantity(after);
        menuItemRepository.save(menuItem);

        StockMovement m = new StockMovement();
        m.setMenuItem(menuItem);
        m.setType(dto.getType());
        m.setQuantity(qty);
        m.setStockBefore(before);
        m.setStockAfter(after);
        m.setReason(dto.getReason());
        m.setReferenceDoc(dto.getReferenceDoc());
        m.setUser(getCurrentUser());

        StockMovement saved = stockMovementRepository.save(m);
        return toDTO(saved);
    }

    @Override
    @Transactional
    public void recordSaleMovement(Long menuItemId, int quantity, String orderRef) {

        MenuItem menuItem = menuItemRepository.findByIdForUpdate(menuItemId).orElse(null);
        if (menuItem == null || quantity <= 0) {
            return;
        }
        int before = menuItem.getStockQuantity() != null ? menuItem.getStockQuantity() : 0;
        int after = before - quantity;

        if (after < 0 && !Boolean.TRUE.equals(menuItem.getAllowNegativeStock())) {
            throw new com.foodsquad.FoodSquad.exception.InsufficientStockException(
                    java.util.List.of(new com.foodsquad.FoodSquad.exception.InsufficientStockException.Item(
                            menuItem.getId(),
                            menuItem.getTitle(),
                            quantity,
                            before
                    ))
            );
        }

        menuItem.setStockQuantity(after);
        menuItemRepository.save(menuItem);

        // FIFO consumption from lots if this item tracks expiry dates
        String lotsTouched = "";
        if (Boolean.TRUE.equals(menuItem.getHasExpiryDate())) {
            java.util.List<Long> ids = stockLotService.consumeFifo(menuItem.getId(), quantity);
            if (!ids.isEmpty()) {
                lotsTouched = " | LOTS:" + ids.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
            }
        }

        StockMovement m = new StockMovement();
        m.setMenuItem(menuItem);
        m.setType(StockMovementType.SALE);
        m.setQuantity(quantity);
        m.setStockBefore(before);
        m.setStockAfter(after);
        m.setReason("Vente");
        m.setReferenceDoc(orderRef + lotsTouched);
        m.setUser(getCurrentUserOrNull());
        stockMovementRepository.save(m);
    }

    @Override
    @Transactional
    public void recordReturnMovement(Long menuItemId, int quantity, String orderRef, String reason) {
        MenuItem menuItem = menuItemRepository.findByIdForUpdate(menuItemId).orElse(null);
        if (menuItem == null || quantity <= 0) return;
        int before = menuItem.getStockQuantity() != null ? menuItem.getStockQuantity() : 0;
        int after = before + quantity;
        menuItem.setStockQuantity(after);
        menuItemRepository.save(menuItem);

        StockMovement m = new StockMovement();
        m.setMenuItem(menuItem);
        m.setType(StockMovementType.IN);
        m.setQuantity(quantity);
        m.setStockBefore(before);
        m.setStockAfter(after);
        m.setReason("Retour client" + (reason != null && !reason.isBlank() ? " — " + reason : ""));
        m.setReferenceDoc(orderRef);
        m.setUser(getCurrentUserOrNull());
        stockMovementRepository.save(m);
    }

    @Override
    @Transactional
    public void recordLossMovement(Long menuItemId, int quantity, String orderRef, String reason) {
        MenuItem menuItem = menuItemRepository.findByIdForUpdate(menuItemId).orElse(null);
        if (menuItem == null || quantity <= 0) return;
        int before = menuItem.getStockQuantity() != null ? menuItem.getStockQuantity() : 0;

        // Loss does NOT decrement again here — the SALE already removed the items from stock.
        // We simply record an audit entry typed LOSS for traceability.
        StockMovement m = new StockMovement();
        m.setMenuItem(menuItem);
        m.setType(StockMovementType.LOSS);
        m.setQuantity(quantity);
        m.setStockBefore(before);
        m.setStockAfter(before);
        m.setReason("Retour non remis en stock — " + (reason != null ? reason : "raison non précisée"));
        m.setReferenceDoc(orderRef);
        m.setUser(getCurrentUserOrNull());
        stockMovementRepository.save(m);
    }

    @Override
    public PaginatedResponseDTO<StockMovementDTO> getMovements(int page, int limit) {

        Pageable pageable = PageRequest.of(page, limit);
        Page<StockMovement> p = stockMovementRepository.findAllByOrderByCreatedAtDesc(pageable);
        List<StockMovementDTO> items = p.getContent().stream().map(this::toDTO).toList();
        return new PaginatedResponseDTO<>(items, p.getTotalElements());
    }

    @Override
    public PaginatedResponseDTO<StockMovementDTO> getMovementsByMenuItem(Long menuItemId, int page, int limit) {

        Pageable pageable = PageRequest.of(page, limit);
        Page<StockMovement> p = stockMovementRepository.findByMenuItemIdOrderByCreatedAtDesc(menuItemId, pageable);
        List<StockMovementDTO> items = p.getContent().stream().map(this::toDTO).toList();
        return new PaginatedResponseDTO<>(items, p.getTotalElements());
    }

    private int computeNewStock(int before, int qty, StockMovementType type) {
        return switch (type) {
            case IN -> before + qty;
            case OUT, LOSS, SALE -> before - qty;
            case ADJUSTMENT -> qty;
        };
    }

    private StockMovementDTO toDTO(StockMovement m) {
        StockMovementDTO dto = new StockMovementDTO();
        dto.setId(m.getId());
        dto.setMenuItemId(m.getMenuItem().getId());
        dto.setMenuItemTitle(m.getMenuItem().getTitle());
        dto.setType(m.getType());
        dto.setQuantity(m.getQuantity());
        dto.setStockBefore(m.getStockBefore());
        dto.setStockAfter(m.getStockAfter());
        dto.setReason(m.getReason());
        dto.setReferenceDoc(m.getReferenceDoc());
        dto.setUserEmail(m.getUser() != null ? m.getUser().getEmail() : null);
        dto.setCreatedAt(m.getCreatedAt());
        return dto;
    }

    private User getCurrentUser() {
        User u = getCurrentUserOrNull();
        if (u == null) throw new IllegalArgumentException("User not authenticated");
        return u;
    }

    private User getCurrentUserOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserDetails ud)) {
            return null;
        }
        return userRepository.findByEmail(ud.getUsername()).orElse(null);
    }
}
