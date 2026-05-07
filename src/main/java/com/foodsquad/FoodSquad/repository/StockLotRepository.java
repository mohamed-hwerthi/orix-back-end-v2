package com.foodsquad.FoodSquad.repository;

import com.foodsquad.FoodSquad.model.entity.StockLot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface StockLotRepository extends JpaRepository<StockLot, Long> {

    /** Lots actifs d'un article, triés FIFO (péremption croissante) */
    @Query("SELECT l FROM StockLot l WHERE l.menuItem.id = :itemId AND l.status = 'ACTIVE' AND l.quantity > 0 ORDER BY l.expiryDate ASC, l.id ASC")
    List<StockLot> findActiveByItemFifo(@Param("itemId") Long itemId);

    /** Tous les lots actifs périmant avant la date donnée */
    @Query("SELECT l FROM StockLot l WHERE l.status = 'ACTIVE' AND l.quantity > 0 AND l.expiryDate <= :before ORDER BY l.expiryDate ASC")
    List<StockLot> findExpiringBefore(@Param("before") LocalDate before);

    /** Tous les lots d'un article (toutes statuts) */
    List<StockLot> findByMenuItemIdOrderByExpiryDateAsc(Long menuItemId);

    /** Comptage des lots périmant dans N jours */
    @Query("SELECT COUNT(l) FROM StockLot l WHERE l.status = 'ACTIVE' AND l.quantity > 0 AND l.expiryDate <= :before")
    long countExpiringBefore(@Param("before") LocalDate before);
}
