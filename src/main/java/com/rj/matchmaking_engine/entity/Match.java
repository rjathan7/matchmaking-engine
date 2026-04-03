package com.rj.matchmaking_engine.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "matches")
public class Match {
    
    @Id
    @Column(name = "match_id", updatable = false, nullable = false)
    private UUID matchId;

    @Column(name = "player1_id", nullable = false)
    private UUID player1Id;

    @Column(name = "player2_id", nullable = false)
    private UUID player2Id;

    @Column(name = "status", nullable = false)
    private String status; // "PENDING" or "COMPLETED"

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) status = "PENDING";
    }
}
