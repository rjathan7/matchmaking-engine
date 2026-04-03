package com.rj.matchmaking_engine.controller;

import com.rj.matchmaking_engine.entity.Player;
import com.rj.matchmaking_engine.service.PlayerService;
import com.rj.matchmaking_engine.service.QueueService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/queue")
public class QueueController {

    private final QueueService queueService;
    private final PlayerService playerService;

    public QueueController(QueueService queueService, PlayerService playerService) {
        this.queueService = queueService;
        this.playerService = playerService;
    }

    // POST /queue/join
    @PostMapping("/join")
    public ResponseEntity<?> joinQueue(@RequestBody Map<String, String> request) {
        try {
            UUID playerId = UUID.fromString(request.get("player_id"));
            Player player = playerService.getPlayer(playerId);

            if (queueService.isPlayerQueued(playerId.toString())) {
                return ResponseEntity.status(409).body(Map.of(
                    "status", "error",
                    "message", "player already in queue"
                ));
            }

            queueService.addToQueue(playerId.toString(), player.getRegion());

            return ResponseEntity.ok(Map.of(
                "status", "queued",
                "player_id", playerId.toString(),
                "region", player.getRegion()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(400).body(Map.of(
                "status", "error",
                "message", e.getMessage()
            ));
        }
    }
}
