package com.rj.matchmaking_engine.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.rj.matchmaking_engine.entity.Player;
import com.rj.matchmaking_engine.repository.PlayerRepository;

@Service
public class PlayerService {
    
    private static final int MAX_PLAYERS = 1000;

    private final PlayerRepository playerRepository;

    public PlayerService(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    public Player createPlayer(UUID player_id, String region, int initialElo) {
        if (playerRepository.count() >= MAX_PLAYERS) {
            throw new IllegalStateException("maximum player limit reached");
        }
        if (playerRepository.existsById(player_id)) {
            throw new IllegalArgumentException("player already exists");
        }

        Player player = new Player();
        player.setPlayerId(player_id);
        player.setRegion(region);
        player.setElo(initialElo);

        return playerRepository.save(player);
    }

    public List<Player> getAllPlayers() {
        return playerRepository.findAll();
    }

    public void deleteAllPlayers() {
        playerRepository.deleteAll();
    }
}
