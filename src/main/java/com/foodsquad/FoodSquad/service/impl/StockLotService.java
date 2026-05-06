package com.foodsquad.FoodSquad.service.impl;

import com.foodsquad.FoodSquad.model.dto.StockLotDTO;
import com.foodsquad.FoodSquad.model.entity.MenuItem;
import com.foodsquad.FoodSquad.model.entity.StockLot;
import com.foodsquad.FoodSquad.repository.MenuItemRepository;
import com.foodsquad.FoodSquad.repository.StockLotRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class StockLotService {

    private final StockLotRepository stockLotRepository;
    private final MenuItemRepository menuItemRepository;

    public StockLotService(StockLotRepository stockLotRepository, MenuItemRepository menuItemRepository) {
        this.stockLotRepository = stockLotRepository;
        this.menuItemRepository = menuItemRepository;
    }

    @Transactional
    public StockLotDTO createLot(StockLotDTO dto) {
        MenuItem item = menuItemRepository.findById(dto.getMenuItemId())
                .orElseThrow(() -> new EntityNotFoundException("MenuItem not found: " + dto.getMenuItemId()));

        StockLot lot = new StockLot();
        lot.setMenuItem(item);
        lot.setBatchNumber(dto.getBatchNumber());
        lot.setQuantity(dto.getQuantity());
        lot.setInitialQuantity(dto.getQuantity());
        lot.setExpiryDate(dto.getExpiryDate());
        lot.setReceivedDate(dto.getReceivedDate() != null ? dto.getReceivedDate() : LocalDate.now());

        // Adjust the global stockQuantity of the menu item
        int currentStock = item.getStockQuantity() != null ? item.getStockQuantity() : 0;
        item.setStockQuantity(currentStock + dto.getQuantity());
        menuItemRepository.save(item);

        return toDTO(stockLotRepository.save(lot));
    }

    public List<StockLotDTO> findByMenuItem(Long menuItemId) {
        return stockLotRepository.findByMenuItemIdOrderByExpiryDateAsc(menuItemId)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<StockLotDTO> findExpiring(int days) {
        LocalDate before = LocalDate.now().plusDays(days);
        return stockLotRepository.findExpiringBefore(before)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    public long countExpiring(int days) {
        return stockLotRepository.countExpiringBefore(LocalDate.now().plusDays(days));
    }

    /**
     * Consume `quantity` from active lots in FIFO (earliest expiry first).
     * Marks fully consumed lots as CONSUMED. Returns the lot IDs touched.
     */
    @Transactional
    public List<Long> consumeFifo(Long menuItemId, int quantity) {
        if (quantity <= 0) return List.of();
        List<StockLot> lots = stockLotRepository.findActiveByItemFifo(menuItemId);
        int remaining = quantity;
        java.util.List<Long> touched = new java.util.ArrayList<>();
        for (StockLot lot : lots) {
            if (remaining <= 0) break;
            int take = Math.min(remaining, lot.getQuantity());
            lot.setQuantity(lot.getQuantity() - take);
            if (lot.getQuantity() == 0) {
                lot.setStatus(StockLot.StockLotStatus.CONSUMED);
            }
            stockLotRepository.save(lot);
            remaining -= take;
            touched.add(lot.getId());
        }
        // If remaining > 0, the global stockQuantity protected by lots is exhausted —
        // but we don't fail here: the sale might be allowed by allowNegativeStock,
        // and OrderService.checkStockAvailability is the authoritative gate.
        return touched;
    }

    @Transactional
    public StockLotDTO markExpired(Long lotId) {
        StockLot lot = stockLotRepository.findById(lotId)
                .orElseThrow(() -> new EntityNotFoundException("Lot not found: " + lotId));
        if (lot.getStatus() == StockLot.StockLotStatus.ACTIVE && lot.getQuantity() > 0) {
            // Decrement the menu item's global stock by the lost quantity
            MenuItem item = lot.getMenuItem();
            int current = item.getStockQuantity() != null ? item.getStockQuantity() : 0;
            item.setStockQuantity(Math.max(0, current - lot.getQuantity()));
            menuItemRepository.save(item);
        }
        lot.setStatus(StockLot.StockLotStatus.EXPIRED);
        lot.setQuantity(0);
        return toDTO(stockLotRepository.save(lot));
    }

    @Transactional
    public void deleteLot(Long lotId) {
        StockLot lot = stockLotRepository.findById(lotId)
                .orElseThrow(() -> new EntityNotFoundException("Lot not found: " + lotId));
        if (lot.getStatus() == StockLot.StockLotStatus.ACTIVE && lot.getQuantity() > 0) {
            MenuItem item = lot.getMenuItem();
            int current = item.getStockQuantity() != null ? item.getStockQuantity() : 0;
            item.setStockQuantity(Math.max(0, current - lot.getQuantity()));
            menuItemRepository.save(item);
        }
        stockLotRepository.delete(lot);
    }

    private StockLotDTO toDTO(StockLot lot) {
        StockLotDTO dto = new StockLotDTO();
        dto.setId(lot.getId());
        dto.setMenuItemId(lot.getMenuItem().getId());
        dto.setMenuItemTitle(lot.getMenuItem().getTitle());
        dto.setBatchNumber(lot.getBatchNumber());
        dto.setQuantity(lot.getQuantity());
        dto.setInitialQuantity(lot.getInitialQuantity());
        dto.setExpiryDate(lot.getExpiryDate());
        dto.setReceivedDate(lot.getReceivedDate());
        dto.setCreatedAt(lot.getCreatedAt());
        dto.setStatus(lot.getStatus().name());
        dto.setDaysUntilExpiry((int) ChronoUnit.DAYS.between(LocalDate.now(), lot.getExpiryDate()));
        return dto;
    }
}
