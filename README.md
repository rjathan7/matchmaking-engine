# Matchmaking Engine

A 1v1 matchmaking engine that pairs players based on Elo rating, region, and queue time. The system simulates production traffic using Docker on a single AWS EC2 free-tier instance, with Redis for fast queue operations and PostgreSQL for durable player ratings.

---

## Architecture

The system consists of four components:

### Matchmaking Service
A REST API that handles queue joins, match creation, and result reporting. It runs a continuous matchmaking worker loop and updates Elo ratings after each match.

### Redis
Used for fast in-memory operations — stores active region-based queues, temporary player state, and active matches. Each region has its own sorted set where the member is `player_id` and the score is the `join_timestamp`.

```
queue:NA    -> sorted set of NA players
queue:EU    -> sorted set of EU players
queue:ASIA  -> sorted set of ASIA players

# Optional Elo cache
player:elo:{player_id} -> cached Elo rating
```

### PostgreSQL
Stores persistent player data so ratings survive restarts and evolve over long simulations.

| Column | Type | Description |
|---|---|---|
| `player_id` | UUID | Primary Key |
| `elo` | INT | Current Elo rating |
| `region` | VARCHAR(10) | NA, EU, or ASIA |
| `created_at` | TIMESTAMP | When player was created |
| `updated_at` | TIMESTAMP | When Elo was last updated |

### Simulation Client
Runs locally on the developer's machine and simulates player behavior by generating players, sending requests to the matchmaking API, and reporting match outcomes.

---

## Matchmaking Logic

1. Players join a Redis sorted set queue for their region, scored by `join_timestamp`
2. The matchmaking worker continuously scans each regional queue, starting with the longest-waiting players
3. For each player, the worker looks up their Elo and searches for opponents within a tolerance range
4. If a match is found, both players are removed from the queue and paired
5. If no match is found, the player stays in the queue and is reconsidered next cycle
6. If wait time exceeds a threshold, the Elo tolerance gradually expands
7. If wait time exceeds a longer threshold, cross-region matching is activated

---

## Elo Formula

```
ExpectedScore = 1 / (1 + 10^((opponentElo - playerElo) / 400))
NewElo = OldElo + K * (ActualScore - ExpectedScore)
```

Match outcomes in the simulation are probabilistically determined based on Elo difference.

---

## Simulation Modes

### Reset Mode
Clears all player data in PostgreSQL and generates a fresh population with random Elo ratings (800–2400) and regions. Useful for controlled, repeatable experiments.

### Continue Mode
Reuses existing players from PostgreSQL, allowing Elo ratings to keep evolving from the previous state. Useful for long-running ecosystem simulations.

A configurable **maximum player limit** prevents unbounded population growth and keeps the EC2 free-tier instance within safe resource limits.

---

## Simulation Flow

1. Generate N players with random Elo and region
2. Insert them into PostgreSQL via the API
3. Send queue join requests to the matchmaking service
4. When matches are formed, simulate outcomes probabilistically
5. Report results to update Elo ratings
6. Randomly select ~60% of players to rejoin the queue
7. Repeat for multiple rounds

---

## API Reference

Base URL: `http://<ec2-public-ip>:<port>`

### `POST /players` — Create Player
```json
// Request
{ "player_id": "uuid", "region": "NA", "initial_elo": 1400 }

// Response
{ "status": "created", "player_id": "uuid", "elo": 1400, "region": "NA" }
```

### `GET /players` — List Players
```json
{
  "players": [
    { "player_id": "uuid1", "elo": 1420, "region": "NA" },
    { "player_id": "uuid2", "elo": 1300, "region": "EU" }
  ]
}
```

### `POST /queue/join` — Join Queue
```json
// Request
{ "player_id": "uuid" }

// Response
{ "status": "queued", "player_id": "uuid", "region": "NA" }
```

### `GET /match/{player_id}` — Get Match Assignment
```json
// Matched
{ "match_id": "match_uuid", "player1_id": "uuid1", "player2_id": "uuid2", "created_at": "timestamp" }

// Still waiting
{ "status": "waiting" }
```

### `POST /match/result` — Report Match Result
```json
// Request
{ "match_id": "match_uuid", "winner_id": "uuid1", "loser_id": "uuid2" }

// Response
{ "status": "processed", "winner_new_elo": 1450, "loser_new_elo": 1370 }
```

### `DELETE /players` — Reset Ecosystem *(optional)*
```json
{ "status": "reset_complete" }
```
Removes all player records from PostgreSQL and clears all Redis queues.

---

## Deployment

The system runs on a **single AWS EC2 free-tier instance** using Docker. Three containers run on the instance:

- `matchmaking-service` — REST API + background matchmaking worker
- `redis` — queue and temporary state
- `postgres` — persistent player ratings

The simulation client runs **locally** on the developer's machine, hitting the EC2 public IP. This keeps compute load off the free-tier instance while still simulating realistic network traffic.

---

## Tech Stack

| Component | Technology |
|---|---|
| API | Java / Spring Boot |
| Queue | Redis (sorted sets) |
| Database | PostgreSQL |
| Containerization | Docker |
| Hosting | AWS EC2 (free tier) |

---

## Scope

- 1v1 matches only
- Cross-region fallback after timeout
- No authentication (simulated players)
- Single EC2 free-tier instance
- Dockerized deployment