package com.foodsquad.FoodSquad.service.impl;

import com.foodsquad.FoodSquad.model.dto.PromotionDTO;
import com.foodsquad.FoodSquad.model.dto.PromotionStatsDTO;
import com.foodsquad.FoodSquad.model.entity.Category;
import com.foodsquad.FoodSquad.model.entity.MenuItem;
import com.foodsquad.FoodSquad.model.entity.Promotion;
import com.foodsquad.FoodSquad.repository.CategoryRepository;
import com.foodsquad.FoodSquad.repository.MenuItemRepository;
import com.foodsquad.FoodSquad.repository.OrderRepository;
import com.foodsquad.FoodSquad.repository.PromotionRepository;
import com.foodsquad.FoodSquad.service.declaration.PromotionService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PromotionServiceImpl implements PromotionService {

    private final PromotionRepository promotionRepository;
    private final MenuItemRepository menuItemRepository;
    private final CategoryRepository categoryRepository;
    private final PromotionEngine promotionEngine;
    private final OrderRepository orderRepository;

    public PromotionServiceImpl(PromotionRepository promotionRepository,
                                MenuItemRepository menuItemRepository,
                                CategoryRepository categoryRepository,
                                PromotionEngine promotionEngine,
                                OrderRepository orderRepository) {
        this.promotionRepository = promotionRepository;
        this.menuItemRepository = menuItemRepository;
        this.categoryRepository = categoryRepository;
        this.promotionEngine = promotionEngine;
        this.orderRepository = orderRepository;
    }

    @Override
    public PromotionDTO validateCode(String code) {
        return promotionEngine.validateCode(code)
                .map(this::toDTO)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Code promo invalide, expiré ou épuisé"));
    }

    @Override
    public PromotionStatsDTO getStats(Long id) {
        Promotion p = promotionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Promotion not found: " + id));
        PromotionStatsDTO dto = baseStats(p);
        // Top products triggered by this promotion (limit 5)
        List<Object[]> rows = orderRepository.topProductsForPromotion(id, 5);
        for (Object[] r : rows) {
            Long itemId = ((Number) r[0]).longValue();
            String title = (String) r[1];
            long qty = ((Number) r[2]).longValue();
            dto.getTopProducts().add(new PromotionStatsDTO.TopProduct(itemId, title, qty));
        }
        return dto;
    }

    @Override
    public List<PromotionStatsDTO> getAllStats() {
        Map<Long, Promotion> byId = new HashMap<>();
        for (Promotion p : promotionRepository.findAll()) byId.put(p.getId(), p);

        Map<Long, PromotionStatsDTO> result = new HashMap<>();
        for (Object[] r : orderRepository.aggregateStatsPerPromotion()) {
            Long pid = ((Number) r[0]).longValue();
            Promotion p = byId.get(pid);
            if (p == null) continue;
            PromotionStatsDTO dto = new PromotionStatsDTO();
            dto.setPromotionId(pid);
            dto.setPromotionName(p.getName());
            dto.setOrdersCount(((Number) r[1]).intValue());
            dto.setRevenue(((Number) r[2]).doubleValue());
            dto.setDiscountTotal(((Number) r[3]).doubleValue());
            dto.setOriginalTotal(((Number) r[4]).doubleValue());
            dto.setAvgDiscountPerOrder(dto.getOrdersCount() > 0 ? dto.getDiscountTotal() / dto.getOrdersCount() : 0.0);
            result.put(pid, dto);
        }
        // Include promos with zero usage
        for (Promotion p : byId.values()) {
            result.computeIfAbsent(p.getId(), pid -> {
                PromotionStatsDTO empty = baseStats(p);
                return empty;
            });
        }
        return new ArrayList<>(result.values());
    }

    @Override
    public List<PromotionStatsDTO.OrderRow> getOrdersUsing(Long id) {
        if (!promotionRepository.existsById(id)) {
            throw new EntityNotFoundException("Promotion not found: " + id);
        }
        List<Object[]> rows = orderRepository.findOrdersUsingPromotion(id);
        List<PromotionStatsDTO.OrderRow> out = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            PromotionStatsDTO.OrderRow row = new PromotionStatsDTO.OrderRow();
            row.setOrderId((String) r[0]);
            row.setCreatedOn(r[1] != null ? r[1].toString() : null);
            row.setTotalCost(r[2] != null ? ((Number) r[2]).doubleValue() : 0.0);
            row.setOriginalAmount(r[3] != null ? ((Number) r[3]).doubleValue() : 0.0);
            row.setDiscountAmount(r[4] != null ? ((Number) r[4]).doubleValue() : 0.0);
            row.setStatus(r[5] != null ? r[5].toString() : null);
            row.setUserEmail((String) r[6]);
            out.add(row);
        }
        return out;
    }

    private PromotionStatsDTO baseStats(Promotion p) {
        PromotionStatsDTO dto = new PromotionStatsDTO();
        dto.setPromotionId(p.getId());
        dto.setPromotionName(p.getName());
        dto.setOrdersCount(0);
        dto.setRevenue(0.0);
        dto.setDiscountTotal(0.0);
        dto.setOriginalTotal(0.0);
        dto.setAvgDiscountPerOrder(0.0);
        return dto;
    }

    @Override
    @Transactional
    public PromotionDTO create(PromotionDTO dto) {
        ensurePromoCodeAvailable(dto.getPromoCode(), null);
        Promotion p = new Promotion();
        applyDto(p, dto);
        return toDTO(promotionRepository.save(p));
    }

    @Override
    @Transactional
    public PromotionDTO update(Long id, PromotionDTO dto) {
        Promotion p = promotionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Promotion not found: " + id));
        ensurePromoCodeAvailable(dto.getPromoCode(), id);
        applyDto(p, dto);
        return toDTO(promotionRepository.save(p));
    }

    @Override
    public void delete(Long id) {
        if (!promotionRepository.existsById(id)) {
            throw new EntityNotFoundException("Promotion not found: " + id);
        }
        promotionRepository.deleteById(id);
    }

    @Override
    public PromotionDTO getById(Long id) {
        return promotionRepository.findById(id).map(this::toDTO)
                .orElseThrow(() -> new EntityNotFoundException("Promotion not found: " + id));
    }

    @Override
    public List<PromotionDTO> getAll() {
        return promotionRepository.findAll().stream().map(this::toDTO).toList();
    }

    @Override
    public List<PromotionDTO> getActive() {
        return promotionRepository.findActiveOn(LocalDate.now()).stream().map(this::toDTO).toList();
    }

    private void ensurePromoCodeAvailable(String code, Long excludeId) {
        if (code == null || code.isBlank()) return;
        promotionRepository.findByPromoCode(code).ifPresent(existing -> {
            if (excludeId == null || !existing.getId().equals(excludeId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Promo code '" + code + "' is already used by another promotion");
            }
        });
    }

    private void applyDto(Promotion p, PromotionDTO dto) {
        p.setName(dto.getName());
        p.setDescription(dto.getDescription());
        p.setType(dto.getType());
        p.setValue(dto.getValue());
        p.setStartDate(dto.getStartDate());
        p.setEndDate(dto.getEndDate());
        p.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : true);
        p.setPromoCode(dto.getPromoCode() != null && dto.getPromoCode().isBlank() ? null : dto.getPromoCode());
        p.setMaxUses(dto.getMaxUses());
        if (p.getUsesCount() == null) p.setUsesCount(0);
        p.setMinOrderAmount(dto.getMinOrderAmount());
        p.setMaxDiscountAmount(dto.getMaxDiscountAmount());
        p.setFirstOrderOnly(Boolean.TRUE.equals(dto.getFirstOrderOnly()));
        p.setOncePerUser(Boolean.TRUE.equals(dto.getOncePerUser()));

        p.setMenuItems(new ArrayList<>(resolveMenuItems(dto.getMenuItemIds())));
        p.setCategories(new ArrayList<>(resolveCategories(dto.getCategoryIds())));
    }

    private List<MenuItem> resolveMenuItems(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        List<MenuItem> found = menuItemRepository.findAllById(ids);
        if (found.size() != new HashSet<>(ids).size()) {
            Set<Long> foundIds = found.stream().map(MenuItem::getId).collect(Collectors.toSet());
            List<Long> missing = ids.stream().distinct().filter(id -> !foundIds.contains(id)).toList();
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown menu item IDs: " + missing);
        }
        return found;
    }

    private List<Category> resolveCategories(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        List<Category> found = categoryRepository.findAllById(ids);
        if (found.size() != new HashSet<>(ids).size()) {
            Set<Long> foundIds = found.stream().map(Category::getId).collect(Collectors.toSet());
            List<Long> missing = ids.stream().distinct().filter(id -> !foundIds.contains(id)).toList();
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown category IDs: " + missing);
        }
        return found;
    }

    private PromotionDTO toDTO(Promotion p) {
        PromotionDTO dto = new PromotionDTO();
        dto.setId(p.getId());
        dto.setName(p.getName());
        dto.setDescription(p.getDescription());
        dto.setType(p.getType());
        dto.setValue(p.getValue());
        dto.setStartDate(p.getStartDate());
        dto.setEndDate(p.getEndDate());
        dto.setIsActive(p.getIsActive());
        dto.setPromoCode(p.getPromoCode());
        dto.setMaxUses(p.getMaxUses());
        dto.setUsesCount(p.getUsesCount() == null ? 0 : p.getUsesCount());
        dto.setMinOrderAmount(p.getMinOrderAmount());
        dto.setMaxDiscountAmount(p.getMaxDiscountAmount());
        dto.setFirstOrderOnly(Boolean.TRUE.equals(p.getFirstOrderOnly()));
        dto.setOncePerUser(Boolean.TRUE.equals(p.getOncePerUser()));
        dto.setMenuItemIds(p.getMenuItems().stream().map(MenuItem::getId).toList());
        dto.setCategoryIds(p.getCategories().stream().map(Category::getId).toList());
        return dto;
    }
}
