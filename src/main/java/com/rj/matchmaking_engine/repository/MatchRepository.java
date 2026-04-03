package com.rj.matchmaking_engine.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.rj.matchmaking_engine.entity.Match;

@Repository
public interface MatchRepository extends JpaRepository<Match, UUID> {
    Optional<Match> findByPlayer1IdAndStatus(UUID player1Id, String status);
    Optional<Match> findByPlayer2IdAndStatus(UUID played2Id, String status);

}
