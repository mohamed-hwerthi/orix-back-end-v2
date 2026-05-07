package com.foodsquad.FoodSquad.model.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "cash_movements", indexes = {
        @Index(name = "idx_movement_session", columnList = "cash_session_id")
})
@Data
public class CashMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cash_session_id", nullable = false)
    private CashSession session;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CashMovementType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CashMovementReason reason;

    @Column(nullable = false)
    private Double amount;

    @Column(length = 500)
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum CashMovementType {
        IN, OUT
    }

    public enum CashMovementReason {
        PURCHASE,        // Achat (sortie : pain, fourniture, etc.)
        WITHDRAWAL,      // Retrait personnel / banque
        DEPOSIT,         // Dépôt extérieur (entrée)
        CHANGE_GIVEN,    // Donné en monnaie à autre caisse
        CHANGE_RECEIVED, // Reçu en monnaie
        OTHER
    }
}
