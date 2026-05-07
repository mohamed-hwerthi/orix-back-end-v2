package com.foodsquad.FoodSquad.repository;

import com.foodsquad.FoodSquad.model.entity.CashMovement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CashMovementRepository extends JpaRepository<CashMovement, Long> {

    List<CashMovement> findBySessionIdOrderByCreatedAtDesc(Long sessionId);
}
