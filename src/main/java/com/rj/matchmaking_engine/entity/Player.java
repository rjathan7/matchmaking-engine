package com.rj.matchmaking_engine.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "players")
public class Player {

    @Id
    @Column(name = "player_id", updatable = false, nullable = false)
    private UUID playerId;

    @Column(nullable = false)
    private int elo;

    @Column(length = 10, nullable = false)
    private String region;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void OnUpdate() {
        updatedAt = LocalDateTime.now();
    }    
}
