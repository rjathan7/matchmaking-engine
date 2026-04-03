package com.rj.matchmaking_engine.service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.rj.matchmaking_engine.entity.Match;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.rj.matchmaking_engine.entity.Player;
import com.rj.matchmaking_engine.repository.MatchRepository;
import com.rj.matchmaking_engine.repository.PlayerRepository;

@Service
public class PlayerService {

    private final MatchRepository matchRepository;
    private static final int MAX_PLAYERS = 1000;
    private static final Set<String> VALID_REGIONS = Set.of("NA", "EU", "ASIA");
    private static final String ELO_CACHE_PREFIX = "player:elo:";

    private final PlayerRepository playerRepository;
    private final QueueService queueService;
    private final RedisTemplate<String, String> redisTemplate;

    public PlayerService(PlayerRepository playerRepository, QueueService queueService,
                         MatchRepository matchRepository, RedisTemplate<String, String> redisTemplate) {
        this.playerRepository = playerRepository;
        this.queueService = queueService;
        this.matchRepository = matchRepository;
        this.redisTemplate = redisTemplate;
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

        Player saved = playerRepository.save(player);
        redisTemplate.opsForValue().set(ELO_CACHE_PREFIX + player_id, String.valueOf(initialElo));
        return saved;
    }

    public Player getPlayer(UUID playerId) {
        return playerRepository.findById(playerId)
            .orElseThrow(() -> new IllegalArgumentException("player not found"));
    }

    public List<Player> getAllPlayers() {
        return playerRepository.findAll();
    }

    public Optional<Match> findActiveMatch(UUID playerId) {
        Optional<Match> match = matchRepository.findByPlayer1IdAndStatus(playerId, "PENDING");
        if (match.isPresent()) return match;
        return matchRepository.findByPlayer2IdAndStatus(playerId, "PENDING");
    }

    public void deleteAllPlayers() {
        matchRepository.deleteAll();
        playerRepository.deleteAll();
        queueService.clearAllQueues();
    }

    // Returns [winnerNewElo, loserNewElo]
    public int[] processMatchResult(UUID matchId, UUID winnerId, UUID loserId) {
        Match match = matchRepository.findById(matchId)
            .orElseThrow(() -> new IllegalArgumentException("match not found"));

        if (!"PENDING".equals(match.getStatus())) {
            throw new IllegalStateException("match already processed");
        }

        Player winner = playerRepository.findById(winnerId)
            .orElseThrow(() -> new IllegalArgumentException("winner not found"));
        Player loser = playerRepository.findById(loserId)
            .orElseThrow(() -> new IllegalArgumentException("loser not found"));

        int winnerNewElo = calculateNewElo(winner.getElo(), loser.getElo(), 1);
        int loserNewElo  = calculateNewElo(loser.getElo(), winner.getElo(), 0);

        winner.setElo(winnerNewElo);
        loser.setElo(loserNewElo);
        playerRepository.save(winner);
        playerRepository.save(loser);
        redisTemplate.opsForValue().set(ELO_CACHE_PREFIX + winnerId, String.valueOf(winnerNewElo));
        redisTemplate.opsForValue().set(ELO_CACHE_PREFIX + loserId, String.valueOf(loserNewElo));

        match.setStatus("COMPLETED");
        matchRepository.save(match);

        return new int[]{winnerNewElo, loserNewElo};
    }

    private int calculateNewElo(int playerElo, int opponentElo, int actualScore) {
        double expected = 1.0 / (1 + Math.pow(10, (opponentElo - playerElo) / 400.0));
        return (int) Math.round(playerElo + 32 * (actualScore - expected));
    }
}
