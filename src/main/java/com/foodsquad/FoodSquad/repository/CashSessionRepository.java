package com.foodsquad.FoodSquad.repository;

import com.foodsquad.FoodSquad.model.entity.CashSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CashSessionRepository extends JpaRepository<CashSession, Long> {

    @Query("SELECT s FROM CashSession s WHERE s.openedBy.id = :userId AND s.status = 'OPEN' ORDER BY s.openedAt DESC")
    Optional<CashSession> findOpenByUser(@Param("userId") String userId);

    @Query("SELECT s FROM CashSession s WHERE s.status = 'OPEN' ORDER BY s.openedAt DESC")
    List<CashSession> findAllOpen();

    List<CashSession> findAllByOrderByOpenedAtDesc();

    @Query("SELECT s FROM CashSession s WHERE s.openedAt BETWEEN :from AND :to ORDER BY s.openedAt DESC")
    List<CashSession> findInPeriod(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
