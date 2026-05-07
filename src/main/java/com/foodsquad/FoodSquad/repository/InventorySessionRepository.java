package com.foodsquad.FoodSquad.repository;

import com.foodsquad.FoodSquad.model.entity.InventorySession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InventorySessionRepository extends JpaRepository<InventorySession, Long> {
    List<InventorySession> findAllByOrderByCreatedAtDesc();
}
