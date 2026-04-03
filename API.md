# API Reference

Base URL: `http://<ec2-public-ip>:8080` (or `http://localhost:8080` for local development)

---

## Players

### `POST /players` — Create a player

Creates a new player with a given UUID, region, and starting Elo. Also caches the Elo in Redis.

**Request:**
```json
{
  "player_id": "uuid",
  "region": "NA",
  "initial_elo": 1400
}
```

**Response `200`:**
```json
{
  "status": "created",
  "player_id": "uuid",
  "elo": 1400,
  "region": "NA"
}
```

**Response `400` — invalid region or missing fields:**
```json
{ "status": "error", "message": "invalid region, must be NA, EU or ASIA" }
```

**Response `409` — player already exists:**
```json
{ "status": "error", "message": "player already exists" }
```

**Response `400` — player limit reached:**
```json
{ "status": "error", "message": "maximum player limit reached" }
```

---

### `GET /players` — List all players

Returns all players currently in Postgres.

**Response `200`:**
```json
{
  "players": [
    { "player_id": "uuid1", "elo": 1420, "region": "NA" },
    { "player_id": "uuid2", "elo": 1300, "region": "EU" }
  ]
}
```

---

### `DELETE /players` — Reset ecosystem

Deletes all players and matches from Postgres and clears all Redis queues. Used to start a fresh simulation.

**Response `200`:**
```json
{ "status": "reset_complete" }
```

---

## Queue

### `POST /queue/join` — Join the matchmaking queue

Adds a player to their region's Redis sorted set queue, scored by the current timestamp.

**Request:**
```json
{ "player_id": "uuid" }
```

**Response `200`:**
```json
{
  "status": "queued",
  "player_id": "uuid",
  "region": "NA"
}
```

**Response `400` — player not found:**
```json
{ "status": "error", "message": "player not found" }
```

**Response `409` — player already in queue:**
```json
{ "status": "error", "message": "player already in queue" }
```

---

## Matches

### `GET /match/{player_id}` — Get match assignment

Checks if a player has been matched. Returns match details if found, or a waiting status if not yet matched.

**Response `200` — matched:**
```json
{
  "match_id": "match-uuid",
  "player1_id": "uuid1",
  "player2_id": "uuid2",
  "created_at": "2026-04-03T12:00:00"
}
```

**Response `200` — still waiting:**
```json
{ "status": "waiting" }
```

**Response `400` — invalid UUID:**
```json
{ "status": "error", "message": "..." }
```

---

### `POST /match/result` — Report match result

Reports the outcome of a match. Updates Elo ratings for both players in Postgres and Redis cache, and marks the match as COMPLETED.

**Request:**
```json
{
  "match_id": "match-uuid",
  "winner_id": "uuid1",
  "loser_id": "uuid2"
}
```

**Response `200`:**
```json
{
  "status": "processed",
  "winner_new_elo": 1450,
  "loser_new_elo": 1370
}
```

**Response `400` — player or match not found:**
```json
{ "status": "error", "message": "match not found" }
```

**Response `409` — match already processed:**
```json
{ "status": "error", "message": "match already processed" }
```
