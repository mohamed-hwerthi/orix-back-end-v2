package com.foodsquad.FoodSquad.repository;

import com.foodsquad.FoodSquad.model.entity.Order;
import com.foodsquad.FoodSquad.model.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, String> {

    /**
     * Sums the quantities of a specific menu item across all orders.
     *
     * @param menuItemId The ID of the menu item.
     * @return The total quantity of the menu item.
     */
    @Query(value = "SELECT SUM(quantity) FROM order_menu_item WHERE menu_item_id = :menuItemId", nativeQuery = true)
    Integer sumQuantityByMenuItemId(@Param("menuItemId") Long menuItemId);

    /**
     * Finds all orders with their associated users and returns them in a pageable format.
     *
     * @param pageable The pageable object containing pagination information.
     * @return A page of orders with their associated users.
     */
    @Query("SELECT o FROM Order o JOIN o.user u")
    Page<Order> findAllOrdersWithUsers(Pageable pageable);

    /**
     * Finds an order by its ID, including the associated user.
     *
     * @param orderId The ID of the order.
     * @return An optional order with the associated user.
     */
    @Query("SELECT o FROM Order o JOIN o.user u WHERE o.id = :orderId")
    Optional<Order> findOrderWithUserById(@Param("orderId") String orderId);

    /**
     * Finds orders by the user ID and returns them in a pageable format.
     *
     * @param userId The ID of the user.
     * @param pageable The pageable object containing pagination information.
     * @return A page of orders for the specified user.
     */
    @Query("SELECT o FROM Order o JOIN o.user u WHERE u.id = :userId")
    Page<Order> findOrdersByUserId(@Param("userId") String userId, Pageable pageable);

    /**
     * Removes references to a menu item from the order_menu_item table.
     *
     * @param menuItemId The ID of the menu item to remove references to.
     */
    @Modifying
    @Query(value = "DELETE FROM order_menu_item WHERE menu_item_id = :menuItemId", nativeQuery = true)
    void removeMenuItemReferences(@Param("menuItemId") Long menuItemId);


    /**
     * Counts the number of orders for a specific user.
     *
     * @param userId The ID of the user.
     * @return The number of orders for the user.
     */
    long countByUserId(String userId);

    /**
     * Finds orders created before a specific date and with a specific status.
     *
     * @param createdOn The date before which the orders were created.
     * @param status The status of the orders.
     * @return A list of orders matching the criteria.
     */
    List<Order> findByCreatedOnBeforeAndStatus(LocalDateTime createdOn, OrderStatus status);

    @Query("SELECT o FROM Order o WHERE o.createdOn BETWEEN :from AND :to ORDER BY o.createdOn DESC")
    List<Order> findByCreatedOnBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query(value = "SELECT DATE(o.created_on) AS day, " +
            "COUNT(*) AS orders_count, " +
            "COALESCE(SUM(o.total_cost), 0) AS revenue " +
            "FROM orders o " +
            "WHERE o.created_on BETWEEN :from AND :to " +
            "GROUP BY DATE(o.created_on) " +
            "ORDER BY day", nativeQuery = true)
    List<Object[]> aggregateDaily(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query(value = "SELECT omi.menu_item_id AS menu_item_id, " +
            "mi.title AS title, " +
            "SUM(omi.quantity) AS quantity_sold, " +
            "SUM(omi.quantity * mi.price) AS revenue " +
            "FROM order_menu_item omi " +
            "JOIN orders o ON o.id = omi.order_id " +
            "JOIN menu_items mi ON mi.id = omi.menu_item_id " +
            "WHERE o.created_on BETWEEN :from AND :to " +
            "GROUP BY omi.menu_item_id, mi.title " +
            "ORDER BY quantity_sold DESC " +
            "LIMIT :limit", nativeQuery = true)
    List<Object[]> topProducts(@Param("from") LocalDateTime from,
                               @Param("to") LocalDateTime to,
                               @Param("limit") int limit);

    @Query(value = "SELECT o.id, o.created_on, o.total_cost, o.original_amount, o.discount_amount, o.status, " +
            "u.email AS user_email " +
            "FROM orders o " +
            "JOIN order_applied_promotions oap ON oap.order_id = o.id " +
            "JOIN users u ON u.id = o.user_id " +
            "WHERE oap.promotion_id = :promoId " +
            "ORDER BY o.created_on DESC", nativeQuery = true)
    List<Object[]> findOrdersUsingPromotion(@Param("promoId") Long promoId);

    @Query(value = "SELECT oap.promotion_id, " +
            "COUNT(DISTINCT o.id) AS orders_count, " +
            "COALESCE(SUM(o.total_cost), 0) AS revenue, " +
            "COALESCE(SUM(o.discount_amount), 0) AS discount_total, " +
            "COALESCE(SUM(o.original_amount), 0) AS original_total " +
            "FROM order_applied_promotions oap " +
            "JOIN orders o ON o.id = oap.order_id " +
            "GROUP BY oap.promotion_id", nativeQuery = true)
    List<Object[]> aggregateStatsPerPromotion();

    @Query(value = "SELECT mi.id, mi.title, SUM(omi.quantity) AS qty " +
            "FROM order_applied_promotions oap " +
            "JOIN orders o ON o.id = oap.order_id " +
            "JOIN order_menu_item omi ON omi.order_id = o.id " +
            "JOIN menu_items mi ON mi.id = omi.menu_item_id " +
            "WHERE oap.promotion_id = :promoId " +
            "GROUP BY mi.id, mi.title " +
            "ORDER BY qty DESC " +
            "LIMIT :limit", nativeQuery = true)
    List<Object[]> topProductsForPromotion(@Param("promoId") Long promoId, @Param("limit") int limit);

    @Query("SELECT o FROM Order o WHERE o.cashSession.id = :sessionId ORDER BY o.createdOn ASC")
    List<Order> findByCashSessionId(@Param("sessionId") Long sessionId);

    @Query(value = "SELECT mi.id, mi.title, SUM(omi.quantity) AS qty, SUM(omi.quantity * mi.price) AS revenue " +
            "FROM orders o " +
            "JOIN order_menu_item omi ON omi.order_id = o.id " +
            "JOIN menu_items mi ON mi.id = omi.menu_item_id " +
            "WHERE o.cash_session_id = :sessionId " +
            "GROUP BY mi.id, mi.title " +
            "ORDER BY qty DESC " +
            "LIMIT :limit", nativeQuery = true)
    List<Object[]> topProductsForSession(@Param("sessionId") Long sessionId, @Param("limit") int limit);
}
