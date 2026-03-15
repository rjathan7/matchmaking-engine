package com.rj.matchmaking_engine.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.rj.matchmaking_engine.entity.Player;

@Repository
public interface PlayerRepository extends JpaRepository<Player, UUID> {
   
}
