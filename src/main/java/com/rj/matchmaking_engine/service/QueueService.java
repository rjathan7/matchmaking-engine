package com.rj.matchmaking_engine.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class QueueService {
    private static final String QUEUE_PREFIX = "queue:";
    private static final String[] REGIONS = {"NA", "EU", "ASIA"};
    private static final Set<String> VALID_REGIONS = Set.of("NA", "EU", "ASIA");

    private final RedisTemplate<String, String> redisTemplate;

    public QueueService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // Add a player to their region's queue with current timestamp as score
    public void addToQueue(String playerId, String region) {
        if (!VALID_REGIONS.contains(region.toUpperCase())) {
        throw new IllegalArgumentException("invalid region: " + region);
        }
        String key = QUEUE_PREFIX + region;
        double score = System.currentTimeMillis();
        redisTemplate.opsForZSet().add(key, playerId, score);
    }

    // Remove player from their region's queue — returns true if they were actually removed
    public boolean removeFromQueue(String playerId, String region) {
        String key = QUEUE_PREFIX + region;
        Long removed = redisTemplate.opsForZSet().remove(key, playerId);
        return removed != null && removed > 0;
    }

    // Get all players in a region queue ordered by wait time (longest first)
    public List<String> getQueuedPlayers(String region) {
        String key = QUEUE_PREFIX + region;
        Set<String> players = redisTemplate.opsForZSet().range(key, 0, -1);
        return players != null ? new ArrayList<>(players) : new ArrayList<>();
    }

    // Get how long a player has been waiting in ms
    public long getWaitTime(String playedId, String region) {
        String key = QUEUE_PREFIX + region;
        Double score = redisTemplate.opsForZSet().score(key, playedId);
        if (score == null) return 0;
        return System.currentTimeMillis() - score.longValue();
    }

    // Check if player is in any queue
    public boolean isPlayerQueued(String playerId) {
        for (String region : REGIONS) {
            Double score = redisTemplate.opsForZSet().score(QUEUE_PREFIX + region, playerId);
            if (score != null) return true;
        }
        return false;
    }

    // Check if player is in a specific region's queue
    public boolean isPlayerQueued(String playerId, String region) {
        return redisTemplate.opsForZSet().score(QUEUE_PREFIX + region, playerId) != null;
    }

    // Clear all region queues (used for reset)
    public void clearAllQueues() {
        for (String region : REGIONS) {
            redisTemplate.delete(QUEUE_PREFIX + region);
        }
    }
}
