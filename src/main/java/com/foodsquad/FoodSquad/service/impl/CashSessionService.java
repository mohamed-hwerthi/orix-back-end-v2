package com.foodsquad.FoodSquad.service.impl;

import com.foodsquad.FoodSquad.model.dto.CashMovementDTO;
import com.foodsquad.FoodSquad.model.dto.CashSessionDTO;
import com.foodsquad.FoodSquad.model.dto.CashSessionSummaryDTO;
import com.foodsquad.FoodSquad.model.entity.*;
import com.foodsquad.FoodSquad.repository.CashMovementRepository;
import com.foodsquad.FoodSquad.repository.CashSessionRepository;
import com.foodsquad.FoodSquad.repository.OrderRepository;
import com.foodsquad.FoodSquad.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CashSessionService {

    private final CashSessionRepository cashSessionRepository;
    private final CashMovementRepository cashMovementRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    public CashSessionService(CashSessionRepository cashSessionRepository,
                              CashMovementRepository cashMovementRepository,
                              OrderRepository orderRepository,
                              UserRepository userRepository) {
        this.cashSessionRepository = cashSessionRepository;
        this.cashMovementRepository = cashMovementRepository;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public CashSessionDTO open(CashSessionDTO dto) {
        User user = currentUserOrThrow();
        cashSessionRepository.findOpenByUser(user.getId()).ifPresent(s -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Une session est déjà ouverte (#" + s.getId() + "). Clôturez-la avant d'en ouvrir une nouvelle.");
        });
        CashSession s = new CashSession();
        s.setOpenedBy(user);
        s.setOpeningAmount(dto.getOpeningAmount() != null ? dto.getOpeningAmount() : 0.0);
        s.setOpeningNotes(dto.getOpeningNotes());
        return toDTO(cashSessionRepository.save(s));
    }

    public CashSessionDTO getCurrent() {
        User user = currentUserOrThrow();
        return cashSessionRepository.findOpenByUser(user.getId())
                .map(this::toDTO)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Aucune session ouverte"));
    }

    public CashSession requireOpenForCurrentUser() {
        User user = currentUserOrThrow();
        return cashSessionRepository.findOpenByUser(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "Aucune session de caisse ouverte. Ouvrez la caisse avant de vendre."));
    }

    public CashSessionDTO findById(Long id) {
        return toDTO(cashSessionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Session not found: " + id)));
    }

    public List<CashSessionDTO> findAll() {
        return cashSessionRepository.findAllByOrderByOpenedAtDesc().stream().map(this::toDTO).toList();
    }

    public CashSessionSummaryDTO summary(Long sessionId) {
        CashSession s = cashSessionRepository.findById(sessionId)
                .orElseThrow(() -> new EntityNotFoundException("Session not found: " + sessionId));
        return computeSummary(s);
    }

    @Transactional
    public CashSessionDTO close(Long sessionId, CashSessionDTO dto) {
        CashSession s = cashSessionRepository.findById(sessionId)
                .orElseThrow(() -> new EntityNotFoundException("Session not found: " + sessionId));
        if (s.getStatus() == CashSession.CashSessionStatus.CLOSED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session déjà clôturée");
        }
        CashSessionSummaryDTO summary = computeSummary(s);

        s.setCountedCash(dto.getCountedCash() != null ? dto.getCountedCash() : 0.0);
        s.setCountedCard(dto.getCountedCard() != null ? dto.getCountedCard() : 0.0);
        s.setExpectedCash(round2(summary.getExpectedCashInDrawer()));
        s.setExpectedCard(round2(summary.getExpectedCardTotal()));
        s.setCashVariance(round2(s.getCountedCash() - s.getExpectedCash()));
        s.setCardVariance(round2(s.getCountedCard() - s.getExpectedCard()));
        s.setClosingNotes(dto.getClosingNotes());
        s.setClosedAt(LocalDateTime.now());
        s.setClosedBy(currentUserOrThrow());
        s.setStatus(CashSession.CashSessionStatus.CLOSED);
        s.setZReportNumber("Z-" + LocalDateTime.now().getYear() + "-" + String.format("%06d", s.getId()));
        return toDTO(cashSessionRepository.save(s));
    }

    @Transactional
    public CashMovementDTO addMovement(Long sessionId, CashMovementDTO dto) {
        CashSession s = cashSessionRepository.findById(sessionId)
                .orElseThrow(() -> new EntityNotFoundException("Session not found: " + sessionId));
        if (s.getStatus() == CashSession.CashSessionStatus.CLOSED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session clôturée — mouvement impossible");
        }
        CashMovement m = new CashMovement();
        m.setSession(s);
        m.setType(dto.getType());
        m.setReason(dto.getReason());
        m.setAmount(dto.getAmount());
        m.setNote(dto.getNote());
        m.setUser(currentUserOrThrow());
        return toMovementDTO(cashMovementRepository.save(m));
    }

    public List<CashMovementDTO> listMovements(Long sessionId) {
        return cashMovementRepository.findBySessionIdOrderByCreatedAtDesc(sessionId)
                .stream().map(this::toMovementDTO).collect(Collectors.toList());
    }

    private CashSessionSummaryDTO computeSummary(CashSession s) {
        CashSessionSummaryDTO sum = new CashSessionSummaryDTO();
        sum.setSessionId(s.getId());
        sum.setOpenedAt(s.getOpenedAt());
        sum.setAsOf(LocalDateTime.now());
        sum.setOpeningAmount(s.getOpeningAmount());

        List<Order> orders = orderRepository.findByCashSessionId(s.getId());
        int unitsSold = 0;
        double totalRevenue = 0;
        double totalDiscount = 0;
        double totalOriginal = 0;
        double cash = 0, card = 0, mixed = 0, other = 0;
        int refundsCount = 0;
        double refundsAmount = 0;

        for (Order o : orders) {
            if (o.getStatus() == OrderStatus.REFUNDED) {
                refundsCount++;
                refundsAmount += o.getTotalCost() != null ? o.getTotalCost() : 0;
                continue;
            }
            if (o.getStatus() == OrderStatus.PARTIALLY_REFUNDED) {
                refundsCount++;
                // We don't yet model partial refund amount precisely — flag it
            }
            int units = o.getMenuItemsWithQuantity() == null ? 0
                    : o.getMenuItemsWithQuantity().values().stream().mapToInt(Integer::intValue).sum();
            unitsSold += units;
            totalRevenue += nz(o.getTotalCost());
            totalDiscount += nz(o.getDiscountAmount());
            totalOriginal += nz(o.getOriginalAmount());

            PaymentMethod pm = o.getPaymentMethod() == null ? PaymentMethod.CASH : o.getPaymentMethod();
            switch (pm) {
                case CASH -> cash += nz(o.getTotalCost());
                case CARD -> card += nz(o.getTotalCost());
                case MIXED -> mixed += nz(o.getTotalCost());
                default -> other += nz(o.getTotalCost());
            }
        }
        sum.setOrdersCount(orders.size());
        sum.setUnitsSold(unitsSold);
        sum.setTotalRevenue(round2(totalRevenue));
        sum.setTotalDiscount(round2(totalDiscount));
        sum.setTotalOriginal(round2(totalOriginal));
        sum.setCashSales(round2(cash));
        sum.setCardSales(round2(card));
        sum.setMixedSales(round2(mixed));
        sum.setOtherSales(round2(other));
        sum.setRefundsCount(refundsCount);
        sum.setRefundsAmount(round2(refundsAmount));

        // Manual cash movements
        double cashIn = 0, cashOut = 0;
        for (CashMovement m : cashMovementRepository.findBySessionIdOrderByCreatedAtDesc(s.getId())) {
            if (m.getType() == CashMovement.CashMovementType.IN) cashIn += m.getAmount();
            else cashOut += m.getAmount();
        }
        sum.setCashIn(round2(cashIn));
        sum.setCashOut(round2(cashOut));

        // Theoretical balances
        sum.setExpectedCashInDrawer(round2(s.getOpeningAmount() + cash + cashIn - cashOut));
        sum.setExpectedCardTotal(round2(card));

        // Top products
        for (Object[] r : orderRepository.topProductsForSession(s.getId(), 5)) {
            CashSessionSummaryDTO.TopProduct tp = new CashSessionSummaryDTO.TopProduct();
            tp.menuItemId = ((Number) r[0]).longValue();
            tp.title = (String) r[1];
            tp.quantitySold = ((Number) r[2]).longValue();
            tp.revenue = ((Number) r[3]).doubleValue();
            sum.getTopProducts().add(tp);
        }
        return sum;
    }

    private double nz(Double v) {
        return v == null ? 0.0 : v;
    }

    private double round2(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private User currentUserOrThrow() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserDetails ud)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utilisateur non authentifié");
        }
        return userRepository.findByEmail(ud.getUsername())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    private CashSessionDTO toDTO(CashSession s) {
        CashSessionDTO dto = new CashSessionDTO();
        dto.setId(s.getId());
        dto.setOpeningAmount(s.getOpeningAmount());
        dto.setOpeningNotes(s.getOpeningNotes());
        dto.setOpenedAt(s.getOpenedAt());
        dto.setOpenedByEmail(s.getOpenedBy() != null ? s.getOpenedBy().getEmail() : null);
        dto.setClosedAt(s.getClosedAt());
        dto.setClosedByEmail(s.getClosedBy() != null ? s.getClosedBy().getEmail() : null);
        dto.setCountedCash(s.getCountedCash());
        dto.setCountedCard(s.getCountedCard());
        dto.setExpectedCash(s.getExpectedCash());
        dto.setExpectedCard(s.getExpectedCard());
        dto.setCashVariance(s.getCashVariance());
        dto.setCardVariance(s.getCardVariance());
        dto.setClosingNotes(s.getClosingNotes());
        dto.setZReportNumber(s.getZReportNumber());
        dto.setStatus(s.getStatus().name());
        return dto;
    }

    private CashMovementDTO toMovementDTO(CashMovement m) {
        CashMovementDTO dto = new CashMovementDTO();
        dto.setId(m.getId());
        dto.setSessionId(m.getSession().getId());
        dto.setType(m.getType());
        dto.setReason(m.getReason());
        dto.setAmount(m.getAmount());
        dto.setNote(m.getNote());
        dto.setUserEmail(m.getUser() != null ? m.getUser().getEmail() : null);
        dto.setCreatedAt(m.getCreatedAt());
        return dto;
    }
}
