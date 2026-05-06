package com.foodsquad.FoodSquad.service.impl;

import com.foodsquad.FoodSquad.model.dto.InventoryCountDTO;
import com.foodsquad.FoodSquad.model.dto.InventorySessionDTO;
import com.foodsquad.FoodSquad.model.dto.StockMovementDTO;
import com.foodsquad.FoodSquad.model.entity.*;
import com.foodsquad.FoodSquad.repository.InventorySessionRepository;
import com.foodsquad.FoodSquad.repository.MenuItemRepository;
import com.foodsquad.FoodSquad.repository.UserRepository;
import com.foodsquad.FoodSquad.service.declaration.StockMovementService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class InventorySessionService {

    private final InventorySessionRepository sessionRepository;
    private final MenuItemRepository menuItemRepository;
    private final UserRepository userRepository;
    private final StockMovementService stockMovementService;

    public InventorySessionService(InventorySessionRepository sessionRepository,
                                   MenuItemRepository menuItemRepository,
                                   UserRepository userRepository,
                                   StockMovementService stockMovementService) {
        this.sessionRepository = sessionRepository;
        this.menuItemRepository = menuItemRepository;
        this.userRepository = userRepository;
        this.stockMovementService = stockMovementService;
    }

    public List<InventorySessionDTO> findAll() {
        return sessionRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toSummaryDTO).toList();
    }

    public InventorySessionDTO findById(Long id) {
        return toFullDTO(sessionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Session not found: " + id)));
    }

    @Transactional
    public InventorySessionDTO create(InventorySessionDTO dto) {
        InventorySession s = new InventorySession();
        s.setLabel(dto.getLabel());
        s.setNotes(dto.getNotes());
        s.setUser(getCurrentUserOrNull());

        // Snapshot of current expected quantities for ALL items
        List<MenuItem> items = menuItemRepository.findAll();
        for (MenuItem item : items) {
            InventoryCount c = new InventoryCount();
            c.setSession(s);
            c.setMenuItem(item);
            int expected = item.getStockQuantity() != null ? item.getStockQuantity() : 0;
            c.setExpectedQuantity(expected);
            c.setCountedQuantity(expected); // default = expected (so undelta'd lines are no-ops)
            s.getCounts().add(c);
        }
        return toFullDTO(sessionRepository.save(s));
    }

    @Transactional
    public InventorySessionDTO updateCounts(Long sessionId, List<InventoryCountDTO> counts) {
        InventorySession s = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new EntityNotFoundException("Session not found: " + sessionId));
        if (s.getStatus() != InventorySession.InventorySessionStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session not editable (status=" + s.getStatus() + ")");
        }
        // Build a map of incoming counts by menuItemId
        java.util.Map<Long, Integer> byId = counts.stream()
                .collect(java.util.stream.Collectors.toMap(InventoryCountDTO::getMenuItemId, InventoryCountDTO::getCountedQuantity));
        for (InventoryCount c : s.getCounts()) {
            Integer counted = byId.get(c.getMenuItem().getId());
            if (counted != null) c.setCountedQuantity(counted);
        }
        return toFullDTO(sessionRepository.save(s));
    }

    @Transactional
    public InventorySessionDTO commit(Long sessionId) {
        InventorySession s = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new EntityNotFoundException("Session not found: " + sessionId));
        if (s.getStatus() != InventorySession.InventorySessionStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Already committed/cancelled");
        }
        // Generate ADJUSTMENT movements for each line with a delta != 0
        for (InventoryCount c : s.getCounts()) {
            if (c.delta() == 0) continue;
            StockMovementDTO mvt = new StockMovementDTO();
            mvt.setMenuItemId(c.getMenuItem().getId());
            mvt.setType(StockMovementType.ADJUSTMENT);
            mvt.setQuantity(c.getCountedQuantity()); // ADJUSTMENT sets stock = qty (per StockMovementServiceImpl)
            mvt.setReason("Inventaire #" + s.getId() + " — " + s.getLabel());
            mvt.setReferenceDoc("INVENTORY:" + s.getId());
            stockMovementService.createMovement(mvt);
        }
        s.setStatus(InventorySession.InventorySessionStatus.COMMITTED);
        s.setCommittedAt(LocalDateTime.now());
        return toFullDTO(sessionRepository.save(s));
    }

    @Transactional
    public InventorySessionDTO cancel(Long sessionId) {
        InventorySession s = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new EntityNotFoundException("Session not found: " + sessionId));
        if (s.getStatus() != InventorySession.InventorySessionStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot cancel a committed session");
        }
        s.setStatus(InventorySession.InventorySessionStatus.CANCELLED);
        return toFullDTO(sessionRepository.save(s));
    }

    private InventorySessionDTO toSummaryDTO(InventorySession s) {
        InventorySessionDTO dto = baseDTO(s);
        int diffs = (int) s.getCounts().stream().filter(c -> c.delta() != 0).count();
        int total = s.getCounts().stream().mapToInt(InventoryCount::delta).sum();
        dto.setItemsWithDiff(diffs);
        dto.setTotalDelta(total);
        return dto;
    }

    private InventorySessionDTO toFullDTO(InventorySession s) {
        InventorySessionDTO dto = toSummaryDTO(s);
        dto.setCounts(s.getCounts().stream().map(this::toCountDTO).collect(Collectors.toList()));
        return dto;
    }

    private InventorySessionDTO baseDTO(InventorySession s) {
        InventorySessionDTO dto = new InventorySessionDTO();
        dto.setId(s.getId());
        dto.setLabel(s.getLabel());
        dto.setNotes(s.getNotes());
        dto.setCreatedAt(s.getCreatedAt());
        dto.setCommittedAt(s.getCommittedAt());
        dto.setStatus(s.getStatus().name());
        dto.setUserEmail(s.getUser() != null ? s.getUser().getEmail() : null);
        return dto;
    }

    private InventoryCountDTO toCountDTO(InventoryCount c) {
        InventoryCountDTO dto = new InventoryCountDTO();
        dto.setId(c.getId());
        dto.setMenuItemId(c.getMenuItem().getId());
        dto.setMenuItemTitle(c.getMenuItem().getTitle());
        dto.setSku(c.getMenuItem().getSku());
        dto.setExpectedQuantity(c.getExpectedQuantity());
        dto.setCountedQuantity(c.getCountedQuantity());
        dto.setDelta(c.delta());
        return dto;
    }

    private User getCurrentUserOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserDetails ud)) return null;
        return userRepository.findByEmail(ud.getUsername()).orElse(null);
    }
}
