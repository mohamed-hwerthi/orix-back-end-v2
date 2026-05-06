package com.foodsquad.FoodSquad.repository;

import com.foodsquad.FoodSquad.model.entity.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    Page<StockMovement> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<StockMovement> findByMenuItemIdOrderByCreatedAtDesc(Long menuItemId, Pageable pageable);
}
