package com.foodsquad.FoodSquad.model.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "cash_sessions", indexes = {
        @Index(name = "idx_session_status", columnList = "status"),
        @Index(name = "idx_session_user_status", columnList = "opened_by_user_id, status")
})
@Data
public class CashSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime openedAt = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "opened_by_user_id", nullable = false)
    private User openedBy;

    @Column(nullable = false)
    private Double openingAmount = 0.0;

    @Column(length = 500)
    private String openingNotes;

    @Column
    private LocalDateTime closedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "closed_by_user_id")
    private User closedBy;

    @Column
    private Double countedCash;

    @Column
    private Double countedCard;

    @Column
    private Double expectedCash;

    @Column
    private Double expectedCard;

    @Column
    private Double cashVariance;

    @Column
    private Double cardVariance;

    @Column(length = 1000)
    private String closingNotes;

    @Column(length = 50, unique = true)
    private String zReportNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CashSessionStatus status = CashSessionStatus.OPEN;

    public enum CashSessionStatus {
        OPEN, CLOSED
    }
}
