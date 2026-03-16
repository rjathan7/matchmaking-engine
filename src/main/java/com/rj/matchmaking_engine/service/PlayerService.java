package com.rj.matchmaking_engine.service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.rj.matchmaking_engine.entity.Player;
import com.rj.matchmaking_engine.repository.PlayerRepository;

@Service
public class PlayerService {
    
    private static final int MAX_PLAYERS = 1000;
    private static final Set<String> VALID_REGIONS = Set.of("NA", "EU", "ASIA");

    private final PlayerRepository playerRepository;
    private final QueueService queueService;

    public PlayerService(PlayerRepository playerRepository, QueueService queueService) {
        this.playerRepository = playerRepository;
        this.queueService = queueService;
    }

    public Player createPlayer(UUID player_id, String region, int initialElo) {
        if (playerRepository.count() >= MAX_PLAYERS) {
            throw new IllegalStateException("maximum player limit reached");
        }
        if (playerRepository.existsById(player_id)) {
            throw new IllegalArgumentException("player already exists");
        }
        if (region == null || !VALID_REGIONS.contains(region.toUpperCase())) {
            throw new IllegalArgumentException("invalid region, must be NA, EU or ASIA");
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
        queueService.clearAllQueues();
    }
}
