package com.rj.matchmaking_engine.controller;

import org.springframework.web.bind.annotation.*;

import com.rj.matchmaking_engine.entity.Player;
import com.rj.matchmaking_engine.service.PlayerService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;



@RestController
@RequestMapping("/players")
public class PlayerController {

    private final PlayerService playerService;

    public PlayerController(PlayerService playerService) {
        this.playerService = playerService;
    }

    // POST /players
    @PostMapping
    public ResponseEntity<?> createPlayer(@RequestBody Map<String, Object> request) {
        try {
            UUID playerId = UUID.fromString((String) request.get("player_id"));
            String region = (String) request.get("region");
            int initialElo = (int) request.get("initial_elo");

            Player player = playerService.createPlayer(playerId, region, initialElo);

            return ResponseEntity.ok(Map.of(
                "status", "created",
                "player_id", player.getPlayerId().toString(),
                "elo", player.getElo(),
                "region", player.getRegion()
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(400).body(Map.of(
                "status", "error",
                "message", e.getMessage()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(409).body(Map.of(
                "status", "error",
                "message", e.getMessage()
            ));
        }
    }

     // GET /players
    @GetMapping
    public ResponseEntity<?> getAllPlayers() {
        List<Player> players = playerService.getAllPlayers();
        List<Map<String, Object>> result = players.stream()
            .map(p -> {
                Map<String, Object> map = new java.util.HashMap<>();
                map.put("player_id", p.getPlayerId().toString());
                map.put("elo", p.getElo());
                map.put("region", p.getRegion());
                return map;
            })
            .toList();

        return ResponseEntity.ok(Map.of("players", result));
    }
    
    // DELETE /players
    @DeleteMapping
    public ResponseEntity<?> deleteAllPlayers() {
        playerService.deleteAllPlayers();
        return ResponseEntity.ok(Map.of("status", "reset_complete"));
    }
    
}
