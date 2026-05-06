package com.foodsquad.FoodSquad.service.impl;

import com.foodsquad.FoodSquad.model.dto.OrderDTO;
import com.foodsquad.FoodSquad.model.entity.*;
import com.foodsquad.FoodSquad.repository.MenuItemRepository;
import com.foodsquad.FoodSquad.repository.OrderRepository;
import com.foodsquad.FoodSquad.repository.UserRepository;
import com.foodsquad.FoodSquad.service.declaration.StockMovementService;
import jakarta.persistence.EntityNotFoundException;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private   final OrderRepository orderRepository;

    private  final  UserRepository userRepository;

    private   final  MenuItemRepository menuItemRepository;

    private final StockMovementService stockMovementService;

    private final PromotionEngine promotionEngine;

    private final CashSessionService cashSessionService;

    public OrderService(OrderRepository orderRepository, UserRepository userRepository, MenuItemRepository menuItemRepository ,  ModelMapper modelMapper, StockMovementService stockMovementService, PromotionEngine promotionEngine, CashSessionService cashSessionService) {

        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.menuItemRepository = menuItemRepository;
        this.modelMapper = modelMapper;
        this.stockMovementService = stockMovementService;
        this.promotionEngine = promotionEngine;
        this.cashSessionService = cashSessionService;
    }

    private ModelMapper modelMapper;

    private static class PricingResult {
        double original;
        double total;
        double discount;
        Set<Long> appliedPromotionIds;
    }

    private PricingResult priceOrder(Map<MenuItem, Integer> menuItemsWithQuantity, String promoCode, String userId) {
        // Pre-compute order subtotal (no promo) for minOrderAmount check
        double original = 0.0;
        for (Map.Entry<MenuItem, Integer> e : menuItemsWithQuantity.entrySet()) {
            original += e.getKey().getPrice() * e.getValue();
        }

        // Build candidate list: auto promos + (optionally) the code promo
        List<Promotion> autoPromos = promotionEngine.loadAutoApplicable();
        List<Promotion> candidates = new ArrayList<>(autoPromos);
        if (promoCode != null && !promoCode.isBlank()) {
            promotionEngine.validateCode(promoCode).ifPresent(candidates::add);
        }

        // Filter candidates by anti-abuse rules (min order, oncePerUser, firstOrderOnly)
        List<Promotion> applicable = promotionEngine.filterEligible(candidates, userId, original);

        // First pass: per-item best promo + accumulate per-promo discount
        double total = 0.0;
        Set<Long> appliedIds = new HashSet<>();
        Map<Long, Double> discountByPromoId = new HashMap<>();
        Map<Long, Promotion> promoById = new HashMap<>();

        for (Map.Entry<MenuItem, Integer> entry : menuItemsWithQuantity.entrySet()) {
            MenuItem item = entry.getKey();
            int qty = entry.getValue();
            PromotionEngine.AppliedPrice ap = promotionEngine.computePrice(item, applicable);
            total += ap.unitPrice * qty;
            if (ap.promotion != null && ap.promotion.getId() != null) {
                Long pid = ap.promotion.getId();
                appliedIds.add(pid);
                promoById.put(pid, ap.promotion);
                discountByPromoId.merge(pid, ap.discount * qty, Double::sum);
            }
        }

        // Second pass: cap each promo's contribution at maxDiscountAmount
        double extraToReturn = 0.0;
        for (Map.Entry<Long, Double> e : discountByPromoId.entrySet()) {
            Promotion p = promoById.get(e.getKey());
            double accumulated = e.getValue();
            double capped = promotionEngine.capPromotionDiscount(p, accumulated);
            if (capped < accumulated) {
                extraToReturn += accumulated - capped;
            }
        }
        total += extraToReturn;

        PricingResult result = new PricingResult();
        result.original = round2(original);
        result.total = round2(total);
        result.discount = round2(result.original - result.total);
        result.appliedPromotionIds = appliedIds;
        return result;
    }

    private double round2(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private User getCurrentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        Object principal = auth.getPrincipal();
        String email;
        if (principal instanceof UserDetails ud) {
            email = ud.getUsername();
        } else if (principal instanceof String s) {
            email = s;
        } else {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "Invalid session");
        }
        if (email == null || email.isBlank() || "anonymousUser".equals(email)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "Session expired");
        }
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.UNAUTHORIZED,
                        "User not found for email: " + email));
    }

    private void checkOwnership(User owner) {
        User currentUser = getCurrentUser();
        if (!currentUser.equals(owner) && !currentUser.getRole().equals(UserRole.ADMIN) && !currentUser.getRole().equals(UserRole.MODERATOR)) {
            throw new IllegalArgumentException("Access denied");
        }
    }

    public ResponseEntity<OrderDTO> createOrder(OrderDTO orderDTO) {
        if (orderDTO.getMenuItemQuantities() == null || orderDTO.getMenuItemQuantities().isEmpty()) {
            throw new IllegalArgumentException("Order must contain at least one menu item");
        }
        User user = userRepository.findByEmail(orderDTO.getUserEmail())
                .orElseThrow(() -> new IllegalArgumentException("User not found with email: " + orderDTO.getUserEmail()));

        Map<MenuItem, Integer> menuItemsWithQuantity = resolveItems(orderDTO.getMenuItemQuantities());
        checkStockAvailability(menuItemsWithQuantity);
        PricingResult pricing = priceOrder(menuItemsWithQuantity, orderDTO.getPromoCode(), user.getId());

        // Require an open cash session for the current cashier
        com.foodsquad.FoodSquad.model.entity.CashSession session = cashSessionService.requireOpenForCurrentUser();

        Order order = new Order();
        order.setUser(user);
        order.setCashSession(session);
        order.setPaymentMethod(orderDTO.getPaymentMethod() != null
                ? orderDTO.getPaymentMethod()
                : com.foodsquad.FoodSquad.model.entity.PaymentMethod.CASH);
        order.setMenuItemsWithQuantity(menuItemsWithQuantity);
        order.setStatus(OrderStatus.valueOf(orderDTO.getStatus().toUpperCase()));
        order.setTotalCost(pricing.total);
        order.setOriginalAmount(pricing.original);
        order.setDiscountAmount(pricing.discount);
        order.setAppliedPromotionIds(pricing.appliedPromotionIds);
        order.setCreatedOn(orderDTO.getCreatedOn());
        order.setPaid(true);

        orderRepository.save(order);

        for (Map.Entry<MenuItem, Integer> entry : menuItemsWithQuantity.entrySet()) {
            stockMovementService.recordSaleMovement(entry.getKey().getId(), entry.getValue(), "ORDER:" + order.getId());
        }

        promotionEngine.incrementUsage(pricing.appliedPromotionIds, user.getId());

        OrderDTO responseDTO = modelMapper.map(order, OrderDTO.class);
        return ResponseEntity.status(HttpStatus.CREATED).body(responseDTO);
    }

    private Map<MenuItem, Integer> resolveItems(Map<Long, Integer> menuItemQuantities) {
        // Single batched query instead of N findById calls
        List<MenuItem> found = menuItemRepository.findAllById(menuItemQuantities.keySet());
        if (found.size() != menuItemQuantities.size()) {
            Set<Long> foundIds = found.stream().map(MenuItem::getId).collect(Collectors.toSet());
            List<Long> missing = menuItemQuantities.keySet().stream()
                    .filter(id -> !foundIds.contains(id))
                    .collect(Collectors.toList());
            throw new IllegalArgumentException("Invalid menu item IDs: " + missing);
        }
        Map<MenuItem, Integer> result = new HashMap<>();
        for (MenuItem item : found) {
            int qty = menuItemQuantities.getOrDefault(item.getId(), 1);
            result.put(item, qty);
        }
        return result;
    }

    private void checkStockAvailability(Map<MenuItem, Integer> items) {
        java.util.List<com.foodsquad.FoodSquad.exception.InsufficientStockException.Item> failed = new java.util.ArrayList<>();
        for (Map.Entry<MenuItem, Integer> e : items.entrySet()) {
            MenuItem item = e.getKey();
            int qty = e.getValue();
            int available = item.getStockQuantity() != null ? item.getStockQuantity() : 0;
            if (qty > available && !Boolean.TRUE.equals(item.getAllowNegativeStock())) {
                failed.add(new com.foodsquad.FoodSquad.exception.InsufficientStockException.Item(
                        item.getId(), item.getTitle(), qty, available));
            }
        }
        if (!failed.isEmpty()) {
            throw new com.foodsquad.FoodSquad.exception.InsufficientStockException(failed);
        }
    }

    public List<OrderDTO> getAllOrders(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdOn"));
        Page<Order> orderPage = orderRepository.findAllOrdersWithUsers(pageable);
        return orderPage.stream()
                .map(order -> modelMapper.map(order, OrderDTO.class))
                .collect(Collectors.toList());
    }

    public List<OrderDTO> getOrdersByUserId(String userId, int page, int size) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));
        checkOwnership(user);
        Pageable pageable = PageRequest.of(page, size);
        Page<Order> orders = orderRepository.findOrdersByUserId(userId, pageable);
        return orders.stream()
                .map(order -> modelMapper.map(order, OrderDTO.class))
                .collect(Collectors.toList());
    }

    public ResponseEntity<OrderDTO>getOrderById(String id) {
        Order order = orderRepository.findOrderWithUserById(id)
                .orElseThrow(() -> new EntityNotFoundException("Order not found for ID: " + id));
        checkOwnership(order.getUser());
        OrderDTO orderDTO = modelMapper.map(order, OrderDTO.class);
        return ResponseEntity.ok(orderDTO);
    }

    public ResponseEntity<OrderDTO> updateOrder(String id, OrderDTO orderDTO) {
        if (orderDTO.getMenuItemQuantities() == null || orderDTO.getMenuItemQuantities().isEmpty()) {
            throw new IllegalArgumentException("Order must contain at least one menu item");
        }

        Order existingOrder = orderRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Order not found for ID: " + id));
        checkOwnership(existingOrder.getUser());

        User user = userRepository.findByEmail(orderDTO.getUserEmail())
                .orElseThrow(() -> new IllegalArgumentException("User not found with email: " + orderDTO.getUserEmail()));
        existingOrder.setUser(user);

        Map<MenuItem, Integer> menuItemsWithQuantity = resolveItems(orderDTO.getMenuItemQuantities());
        checkStockAvailability(menuItemsWithQuantity);
        PricingResult pricing = priceOrder(menuItemsWithQuantity, orderDTO.getPromoCode(), user.getId());

        existingOrder.setMenuItemsWithQuantity(menuItemsWithQuantity);
        existingOrder.setStatus(OrderStatus.valueOf(orderDTO.getStatus().toUpperCase()));
        existingOrder.setTotalCost(pricing.total);
        existingOrder.setOriginalAmount(pricing.original);
        existingOrder.setDiscountAmount(pricing.discount);
        existingOrder.setAppliedPromotionIds(pricing.appliedPromotionIds);
        existingOrder.setCreatedOn(orderDTO.getCreatedOn());
        existingOrder.setPaid(orderDTO.getPaid());

        orderRepository.save(existingOrder);
        OrderDTO updatedOrderDTO = modelMapper.map(existingOrder, OrderDTO.class);
        return ResponseEntity.ok(updatedOrderDTO);
    }


    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<OrderDTO> refundOrder(String id, com.foodsquad.FoodSquad.model.dto.RefundRequestDTO request) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Order not found for ID: " + id));
        if (order.getStatus() == OrderStatus.REFUNDED) {
            throw new IllegalArgumentException("Order is already fully refunded");
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("No items to refund");
        }

        // Build a quick lookup of remaining quantities on the order
        Map<Long, Integer> remaining = new HashMap<>();
        for (Map.Entry<MenuItem, Integer> e : order.getMenuItemsWithQuantity().entrySet()) {
            remaining.merge(e.getKey().getId(), e.getValue(), Integer::sum);
        }

        boolean restockable = !"DAMAGED".equalsIgnoreCase(request.getReason())
                && !"EXPIRED".equalsIgnoreCase(request.getReason());

        int totalRefundedUnits = 0;
        for (com.foodsquad.FoodSquad.model.dto.RefundRequestDTO.RefundLine line : request.getItems()) {
            Integer available = remaining.get(line.getMenuItemId());
            if (available == null || available <= 0) {
                throw new IllegalArgumentException("Item " + line.getMenuItemId() + " not in this order");
            }
            int qty = Math.min(line.getQuantity(), available);
            if (qty <= 0) continue;

            if (restockable) {
                stockMovementService.recordReturnMovement(
                        line.getMenuItemId(), qty,
                        "REFUND:" + order.getId(),
                        request.getReason());
            } else {
                stockMovementService.recordLossMovement(
                        line.getMenuItemId(), qty,
                        "REFUND:" + order.getId(),
                        request.getReason());
            }
            totalRefundedUnits += qty;
        }

        // Determine partial vs full refund: count total units in order
        int totalUnits = order.getMenuItemsWithQuantity().values().stream().mapToInt(Integer::intValue).sum();
        order.setStatus(totalRefundedUnits >= totalUnits ? OrderStatus.REFUNDED : OrderStatus.PARTIALLY_REFUNDED);
        orderRepository.save(order);

        OrderDTO responseDTO = modelMapper.map(order, OrderDTO.class);
        return ResponseEntity.ok(responseDTO);
    }

    public ResponseEntity<Map<String, String>> deleteOrder(String id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Order not found for ID: " + id));
        checkOwnership(order.getUser());
        orderRepository.delete(order);
        return ResponseEntity.ok(Map.of("message", "Order successfully deleted"));
    }

    public ResponseEntity<Map<String, String>> deleteOrders(List<String> ids) {
        List<Order> orders = orderRepository.findAllById(ids);
        if (orders.isEmpty()) {
            throw new EntityNotFoundException("No Orders found for the given IDs");
        }
        for (Order order : orders) {
            checkOwnership(order.getUser());
        }
        orderRepository.deleteAll(orders);
        return ResponseEntity.ok(Map.of("message", "Orders successfully deleted"));
    }
    public  Order  getSimpleOrderById(String OrderId){
        return  orderRepository.findById(OrderId).orElseThrow(() -> new EntityNotFoundException("Order not found for ID: " + OrderId));

    }

}
