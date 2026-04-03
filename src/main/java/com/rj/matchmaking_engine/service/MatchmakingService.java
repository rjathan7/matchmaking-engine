package com.rj.matchmaking_engine.service;

import com.rj.matchmaking_engine.entity.Match;
import com.rj.matchmaking_engine.repository.MatchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class MatchmakingService {

    private static final Logger log = LoggerFactory.getLogger(MatchmakingService.class);

    // Elo tolerance starts at 100, expands by 100 every 10 seconds
    private static final int BASE_ELO_TOLERANCE = 100;
    private static final long EXPAND_THRESHOLD_MS = 10_000;   // 10 seconds
    private static final long CROSS_REGION_THRESHOLD_MS = 30_000; // 30 seconds
    private static final String[] REGIONS = {"NA", "EU", "ASIA"};
    private static final String ELO_CACHE_PREFIX = "player:elo:";

    private final QueueService queueService;
    private final MatchRepository matchRepository;
    private final RedisTemplate<String, String> redisTemplate;

    public MatchmakingService(QueueService queueService,
                               MatchRepository matchRepository,
                               RedisTemplate<String, String> redisTemplate) {
        this.queueService = queueService;
        this.matchRepository = matchRepository;
        this.redisTemplate = redisTemplate;
    }

    // Runs every 2 seconds
    @Scheduled(fixedDelay = 2000)
    public void runMatchmakingCycle() {
        try {
            for (String region : REGIONS) {
                processRegionQueue(region);
            }
        } catch (Exception e) {
            log.error("Matchmaking cycle failed", e);
        }
    }

    private void processRegionQueue(String region) {
        List<String> players = queueService.getQueuedPlayers(region);

        for (String playerId : players) {
            // Skip if player was already matched in this cycle
            if (!queueService.isPlayerQueued(playerId, region)) continue;

            long waitTime = queueService.getWaitTime(playerId, region);
            int eloTolerance = calculateEloTolerance(waitTime);

            Integer playerElo = getElo(playerId);
            if (playerElo == null) {
                queueService.removeFromQueue(playerId, region);
                continue;
            }

            // Try to find match in same region first
            String opponent = findOpponent(playerId, playerElo, eloTolerance, region, players);

            // Cross-region fallback after threshold
            if (opponent == null && waitTime >= CROSS_REGION_THRESHOLD_MS) {
                opponent = findOpponentCrossRegion(playerId, playerElo, eloTolerance, region);
            }

            if (opponent != null) {
                createMatch(playerId, opponent, region);
            }
        }
    }

    private String findOpponent(String playerId, int playerElo, int eloTolerance,
                                 String region, List<String> candidates) {
        String bestOpponent = null;
        int bestEloDiff = Integer.MAX_VALUE;

        for (String candidateId : candidates) {
            if (candidateId.equals(playerId)) continue;
            if (!queueService.isPlayerQueued(candidateId, region)) continue;

            Integer candidateElo = getElo(candidateId);
            if (candidateElo == null) continue;

            int eloDiff = Math.abs(playerElo - candidateElo);
            if (eloDiff <= eloTolerance && eloDiff < bestEloDiff) {
                bestEloDiff = eloDiff;
                bestOpponent = candidateId;
            }
        }

        return bestOpponent;
    }

    private String findOpponentCrossRegion(String playerId, int playerElo,
                                            int eloTolerance, String currentRegion) {
        for (String region : REGIONS) {
            if (region.equals(currentRegion)) continue;
            List<String> candidates = queueService.getQueuedPlayers(region);
            String opponent = findOpponent(playerId, playerElo, eloTolerance, region, candidates);
            if (opponent != null) return opponent;
        }
        return null;
    }

    private void createMatch(String player1Id, String player2Id, String region) {
        // Remove both players from all regions (handles same-region and cross-region matches)
        for (String r : REGIONS) {
            queueService.removeFromQueue(player1Id, r);
            queueService.removeFromQueue(player2Id, r);
        }

        Match match = new Match();
        match.setMatchId(UUID.randomUUID());
        match.setPlayer1Id(UUID.fromString(player1Id));
        match.setPlayer2Id(UUID.fromString(player2Id));
        match.setStatus("PENDING");

        matchRepository.save(match);
    }

    private int calculateEloTolerance(long waitTimeMs) {
        int expansions = (int) (waitTimeMs / EXPAND_THRESHOLD_MS);
        return BASE_ELO_TOLERANCE + (expansions * 100);
    }

    // Cache-first Elo lookup — falls back to null if player not found
    private Integer getElo(String playerId) {
        String cached = redisTemplate.opsForValue().get(ELO_CACHE_PREFIX + playerId);
        if (cached != null) return Integer.parseInt(cached);
        return null;
    }
}