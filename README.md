# Matchmaking Engine

A 1v1 matchmaking engine that pairs players based on Elo rating, region, and queue wait time. Built with Java/Spring Boot, Redis, and PostgreSQL. Deployed on a single AWS EC2 free-tier instance using Docker. A local Python simulation client drives traffic against the deployed service.

---

## What This Project Does

Players are created with an Elo rating (skill score) and a region (NA, EU, ASIA). They join a queue. A background worker runs every 2 seconds, scanning each region's queue and pairing players whose Elo ratings are close enough. Once paired, a match is created. The simulation client reports the result, Elo ratings are updated, and players rejoin the queue for the next round.

---

## Project Structure

```
matchmaking-service/
  src/
    main/java/com/rj/matchmaking_engine/
      controller/
        PlayerController.java       # HTTP endpoints for player management
        QueueController.java        # HTTP endpoint for joining the queue
        MatchController.java        # HTTP endpoints for match lookup and result reporting
      service/
        PlayerService.java          # Player creation, Elo updates, match result processing
        QueueService.java           # Redis queue operations
        MatchmakingService.java     # Background worker — pairs players every 2 seconds
      entity/
        Player.java                 # PostgreSQL player table mapping
        Match.java                  # PostgreSQL matches table mapping
      repository/
        PlayerRepository.java       # JPA queries for players
        MatchRepository.java        # JPA queries for matches
    resources/
      application.properties        # Config for local development (localhost)
      application-docker.properties # Config for Docker (uses container service names)
  simulation/
    simulate.py                     # Python async simulation client
  Dockerfile                        # Two-stage build: Maven compiles, JRE runs
  docker-compose.yml                # Spins up app + Redis + Postgres containers
```

---

## Architecture

Three components run on EC2 in Docker containers:

### 1. Matchmaking Service (Spring Boot)
The REST API and background worker. Handles player creation, queue joins, match creation, and Elo updates. The background worker (`@Scheduled`) runs every 2 seconds and processes all regional queues.

### 2. Redis
Stores the active queues as sorted sets. Each player's score is their join timestamp, so the longest-waiting player is always processed first.

```
queue:NA    → sorted set of player IDs waiting in North America
queue:EU    → sorted set of player IDs waiting in Europe
queue:ASIA  → sorted set of player IDs waiting in Asia

player:elo:{player_id} → cached Elo rating (avoids hitting Postgres during matchmaking)
```

### 3. PostgreSQL
Stores persistent player data (Elo, region, timestamps) and match records. Elo ratings survive restarts and evolve across long simulations.

**Players table:**

| Column | Type | Description |
|---|---|---|
| player_id | UUID | Primary key |
| elo | INT | Current Elo rating |
| region | VARCHAR(10) | NA, EU, or ASIA |
| created_at | TIMESTAMP | When player was created |
| updated_at | TIMESTAMP | When Elo was last updated |

**Matches table:**

| Column | Type | Description |
|---|---|---|
| match_id | UUID | Primary key |
| player1_id | UUID | First player |
| player2_id | UUID | Second player |
| status | VARCHAR | PENDING or COMPLETED |
| created_at | TIMESTAMP | When match was created |

### 4. Simulation Client (Python, runs locally)
An async Python script that generates players, sends queue join requests, polls for matches, simulates outcomes probabilistically, and reports results. Runs on the developer's local machine and hits the EC2 public IP. Keeps compute load off the free-tier instance.

---

## Matchmaking Logic

The worker runs every 2 seconds and processes each region independently:

1. Fetch all players in the region's queue, ordered by wait time (longest first)
2. For each player, calculate their current Elo tolerance based on how long they've waited
3. Look up the player's Elo from Redis cache (fast, avoids DB hit)
4. Scan the queue for the closest Elo match within tolerance
5. If no match found and wait time exceeds 30 seconds, search other regions (cross-region fallback)
6. If a match is found, remove both players from all queues and save a PENDING match to Postgres

**Elo tolerance expansion:**
- Starts at ±100
- Expands by 100 every 10 seconds
- After 30 seconds, cross-region matching activates

This means tight, fair matches happen quickly. Players who wait longer accept progressively wider Elo gaps to avoid waiting forever. In small queues, the system will eventually match any two remaining players even if their Elos are far apart.

---

## Elo Formula

```
ExpectedScore = 1 / (1 + 10^((opponentElo - playerElo) / 400))
NewElo = OldElo + K * (ActualScore - ExpectedScore)
```

- K = 32
- Winner: ActualScore = 1
- Loser: ActualScore = 0

Match outcomes in the simulation are probabilistically determined — a higher Elo player is more likely to win, but upsets can happen.

---

## Redis Elo Cache

When a player is created, their Elo is written to Redis at `player:elo:{player_id}`. When Elo changes after a match, both players' cache entries are updated. The matchmaking worker reads Elo exclusively from Redis, never hitting Postgres during matchmaking cycles. This keeps the worker fast even with large queues.

---

## Simulation Client

Located at `simulation/simulate.py`. Uses Python `asyncio` and `aiohttp` for concurrent HTTP requests — all players join the queue and poll for matches simultaneously, simulating realistic concurrent traffic.

**Simulation modes:**

- **Reset mode** — clears all player data, generates a fresh population with random Elo (800–2400) and regions, runs N rounds
- **Continue mode** — loads existing players from Postgres, lets Elo ratings keep evolving from the previous state

**Each round:**
1. All active players join the queue concurrently
2. Each player polls `GET /match/{player_id}` every 2 seconds until matched or timed out (120s)
3. Only player1 reports the result (prevents double-reporting the same match)
4. Win probability is calculated from Elo difference
5. `POST /match/result` is called, Elo ratings update
6. ~60% of players are randomly selected to rejoin next round (simulates churn)

**Install dependency:**
```bash
pip install aiohttp
```

**Run against local app:**
```bash
python simulation/simulate.py --mode reset --players 100 --rounds 5
```

**Run against EC2:**
```bash
python simulation/simulate.py --mode reset --players 100 --rounds 5 --url http://<ec2-public-ip>:8080
```

**Arguments:**

| Argument | Default | Description |
|---|---|---|
| --mode | reset | reset or continue |
| --players | 100 | Number of players to generate (reset mode only) |
| --rounds | 5 | Number of simulation rounds |
| --url | http://localhost:8080 | Base URL of the matchmaking service |

**Sample output:**
```
--- Round 1/3 (50 players) ---
[MATCH] 495bf162 vs 4cf1fa89 | winner: 495bf162 | elos: 1923 / 1899 | wait: 2.1s
[MATCH] 74a9ec68 vs 85216c5e | winner: 74a9ec68 | elos: 2337 / 2254 | wait: 12.2s
[MATCH] a5ddbbb5 vs b57326ed | winner: a5ddbbb5 | elos: 2392 / 2095 | wait: 32.3s
[TIMEOUT] 17fc94d1 never matched
```

Wait times show matchmaking quality:
- **2s** — matched in the first worker cycle
- **12s** — Elo tolerance expanded once
- **30s+** — cross-region fallback activated
- **TIMEOUT** — no compatible opponent found in 120 seconds

---

## Deployment

### Prerequisites
- Docker and Docker Compose installed on EC2
- Port 8080 open in the EC2 security group

### Steps

```bash
# SSH into EC2
ssh -i your-key.pem ec2-user@<ec2-public-ip>

# Clone the repo
git clone <repo-url>
cd matchmaking-service

# Start all containers
docker compose up -d

# Check logs
docker logs matchmaking-service
```

The `depends_on` healthchecks in `docker-compose.yml` ensure Postgres and Redis are fully ready before the Spring Boot app starts.

### Running locally (development)

Start Postgres and Redis via Docker, run Spring Boot directly:

```bash
# Start dependencies only
docker compose up postgres redis -d

# Run the app
mvn spring-boot:run

# Or with the JAR
mvn package -DskipTests
java -Xms128m -Xmx256m -jar target/matchmaking-engine-0.0.1-SNAPSHOT.jar
```

---

## EC2 Free Tier Considerations

The setup is designed to fit within the t2.micro free tier (1 vCPU, 1GB RAM):

| Container | Memory usage |
|---|---|
| Spring Boot | ~256MB (JVM heap capped) |
| PostgreSQL | ~150MB |
| Redis | ~50MB |
| OS + Docker | ~150MB |
| **Total** | **~606MB** |

The JVM heap is capped in the Dockerfile (`-Xms128m -Xmx256m`) to prevent the JVM from consuming too much memory. The max player limit of 1000 prevents unbounded Postgres and Redis growth.

---

## Tech Stack

| Component | Technology |
|---|---|
| API + Worker | Java 21 / Spring Boot |
| Queue | Redis (sorted sets) |
| Database | PostgreSQL |
| Containerization | Docker / Docker Compose |
| Hosting | AWS EC2 (free tier) |
| Simulation client | Python 3 / asyncio / aiohttp |

---

## Scope and Limitations

- 1v1 matches only
- No authentication — players are identified by UUID
- Single EC2 free-tier instance — not horizontally scalable
- Simulation client runs locally, not on the server
- Match duration is instant in simulation — no real game time modeled
- Players join in bursts per round rather than continuously
