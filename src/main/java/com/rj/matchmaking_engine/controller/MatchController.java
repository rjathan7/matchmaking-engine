package com.rj.matchmaking_engine.controller;

import com.rj.matchmaking_engine.entity.Match;
import com.rj.matchmaking_engine.service.PlayerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/match")
public class MatchController {

    private final PlayerService playerService;

    public MatchController(PlayerService playerService) {
        this.playerService = playerService;
    }

    // GET /match/{player_id}
    @GetMapping("/{player_id}")
    public ResponseEntity<?> getMatch(@PathVariable("player_id") String playerIdStr) {
        try {
            UUID playerId = UUID.fromString(playerIdStr);
            Optional<Match> matchOpt = playerService.findActiveMatch(playerId);

            if (matchOpt.isEmpty()) {
                return ResponseEntity.ok(Map.of("status", "waiting"));
            }

            Match match = matchOpt.get();
            return ResponseEntity.ok(Map.of(
                "match_id", match.getMatchId().toString(),
                "player1_id", match.getPlayer1Id().toString(),
                "player2_id", match.getPlayer2Id().toString(),
                "created_at", match.getCreatedAt().toString()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(400).body(Map.of(
                "status", "error",
                "message", e.getMessage()
            ));
        }
    }

    // POST /match/result
    @PostMapping("/result")
    public ResponseEntity<?> reportResult(@RequestBody Map<String, String> request) {
        try {
            UUID matchId  = UUID.fromString(request.get("match_id"));
            UUID winnerId = UUID.fromString(request.get("winner_id"));
            UUID loserId  = UUID.fromString(request.get("loser_id"));

            int[] newElos = playerService.processMatchResult(matchId, winnerId, loserId);

            return ResponseEntity.ok(Map.of(
                "status", "processed",
                "winner_new_elo", newElos[0],
                "loser_new_elo", newElos[1]
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of(
                "status", "error",
                "message", e.getMessage()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(400).body(Map.of(
                "status", "error",
                "message", e.getMessage()
            ));
        }
    }
}
