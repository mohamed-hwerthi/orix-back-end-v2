package com.foodsquad.FoodSquad.service.impl;

import com.foodsquad.FoodSquad.model.entity.Category;
import com.foodsquad.FoodSquad.model.entity.MenuItem;
import com.foodsquad.FoodSquad.model.entity.Promotion;
import com.foodsquad.FoodSquad.model.entity.PromotionType;
import com.foodsquad.FoodSquad.repository.OrderRepository;
import com.foodsquad.FoodSquad.repository.PromotionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PromotionEngine {

    private final PromotionRepository promotionRepository;
    private final OrderRepository orderRepository;

    public PromotionEngine(PromotionRepository promotionRepository, OrderRepository orderRepository) {
        this.promotionRepository = promotionRepository;
        this.orderRepository = orderRepository;
    }

    public static class AppliedPrice {
        public final double unitPrice;
        public final double discount;
        public final Promotion promotion;

        public AppliedPrice(double unitPrice, double discount, Promotion promotion) {
            this.unitPrice = unitPrice;
            this.discount = discount;
            this.promotion = promotion;
        }
    }

    public List<Promotion> loadAutoApplicable() {
        return promotionRepository.findActiveOn(LocalDate.now()).stream()
                .filter(p -> p.getPromoCode() == null || p.getPromoCode().isBlank())
                .filter(this::hasUsesRemaining)
                .collect(Collectors.toList());
    }

    public boolean hasUsesRemaining(Promotion p) {
        if (p.getMaxUses() == null) return true;
        Integer used = p.getUsesCount() == null ? 0 : p.getUsesCount();
        return used < p.getMaxUses();
    }

    /**
     * Filter promotions that fail per-user anti-abuse rules.
     * Use orderTotalBeforeDiscount to filter by minOrderAmount.
     */
    public List<Promotion> filterEligible(List<Promotion> candidates, String userId, double orderTotalBeforeDiscount) {
        return candidates.stream()
                .filter(p -> isEligible(p, userId, orderTotalBeforeDiscount))
                .collect(Collectors.toList());
    }

    public boolean isEligible(Promotion p, String userId, double orderTotalBeforeDiscount) {
        // Min order amount
        if (p.getMinOrderAmount() != null && orderTotalBeforeDiscount < p.getMinOrderAmount()) {
            return false;
        }
        if (userId == null) {
            // Cannot enforce per-user rules if no user; allow code-only rules
            return !Boolean.TRUE.equals(p.getOncePerUser()) && !Boolean.TRUE.equals(p.getFirstOrderOnly());
        }
        // Once per user
        if (Boolean.TRUE.equals(p.getOncePerUser())
                && p.getUsedByUserIds() != null
                && p.getUsedByUserIds().contains(userId)) {
            return false;
        }
        // First order only
        if (Boolean.TRUE.equals(p.getFirstOrderOnly())
                && orderRepository.countByUserId(userId) > 0) {
            return false;
        }
        return true;
    }

    @Transactional
    public void incrementUsage(Set<Long> promotionIds, String userId) {
        if (promotionIds == null || promotionIds.isEmpty()) return;
        for (Long id : promotionIds) {
            promotionRepository.findByIdForUpdate(id).ifPresent(p -> {
                int next = (p.getUsesCount() == null ? 0 : p.getUsesCount()) + 1;
                p.setUsesCount(next);
                if (userId != null) {
                    if (p.getUsedByUserIds() == null) p.setUsedByUserIds(new HashSet<>());
                    p.getUsedByUserIds().add(userId);
                }
                promotionRepository.save(p);
            });
        }
    }

    public AppliedPrice computePrice(MenuItem item, List<Promotion> activePromotions) {
        Promotion best = activePromotions.stream()
                .filter(p -> matches(p, item))
                .max((a, b) -> Double.compare(discountFor(a, item.getPrice()), discountFor(b, item.getPrice())))
                .orElse(null);

        double basePrice = item.getPrice();
        if (best == null) {
            return new AppliedPrice(round2(basePrice), 0.0, null);
        }
        double newPrice = applyTo(best, basePrice);
        return new AppliedPrice(round2(newPrice), round2(basePrice - newPrice), best);
    }

    /**
     * Cap the total discount of a promotion across the order to maxDiscountAmount.
     * If exceeded, scale down proportionally per affected line.
     */
    public double capPromotionDiscount(Promotion p, double accumulatedDiscount) {
        if (p.getMaxDiscountAmount() == null) return accumulatedDiscount;
        return Math.min(accumulatedDiscount, p.getMaxDiscountAmount());
    }

    public Optional<Promotion> validateCode(String code) {
        if (code == null || code.isBlank()) return Optional.empty();
        LocalDate today = LocalDate.now();
        return promotionRepository.findByPromoCode(code)
                .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                .filter(p -> !today.isBefore(p.getStartDate()) && !today.isAfter(p.getEndDate()))
                .filter(this::hasUsesRemaining);
    }

    private boolean matches(Promotion p, MenuItem item) {
        boolean noTarget = (p.getMenuItems() == null || p.getMenuItems().isEmpty())
                && (p.getCategories() == null || p.getCategories().isEmpty());
        if (noTarget) return true;

        if (p.getMenuItems() != null
                && p.getMenuItems().stream().anyMatch(mi -> mi.getId().equals(item.getId()))) {
            return true;
        }
        if (p.getCategories() != null && !p.getCategories().isEmpty()
                && item.getCategories() != null) {
            Set<Long> promoCatIds = p.getCategories().stream().map(Category::getId).collect(Collectors.toSet());
            return item.getCategories().stream().anyMatch(c -> promoCatIds.contains(c.getId()));
        }
        return false;
    }

    private double applyTo(Promotion p, double price) {
        PromotionType type = p.getType();
        double value = p.getValue() == null ? 0 : p.getValue();
        switch (type) {
            case PERCENT:
                return Math.max(0, price * (1 - value / 100.0));
            case FIXED_AMOUNT:
                return Math.max(0, price - value);
            case FIXED_PRICE:
                return Math.max(0, value);
            default:
                return price;
        }
    }

    private double discountFor(Promotion p, double price) {
        return price - applyTo(p, price);
    }

    private double round2(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
