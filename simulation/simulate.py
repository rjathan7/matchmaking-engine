import asyncio
import aiohttp
import argparse
import random
import uuid

# --- Config ---
REGIONS = ["NA", "EU", "ASIA"]
DEFAULT_URL = "http://localhost:8080"
POLL_INTERVAL_S = 2
POLL_TIMEOUT_S = 120


# --- Player generation ---

def make_player():
    return {
        "player_id": str(uuid.uuid4()),
        "region": random.choice(REGIONS),
        "initial_elo": random.randint(800, 2400),
    }


def win_probability(player_elo, opponent_elo):
    return 1 / (1 + 10 ** ((opponent_elo - player_elo) / 400))


# --- API calls ---

async def create_player(session, base_url, player):
    async with session.post(f"{base_url}/players", json=player) as r:
        return await r.json()


async def get_players(session, base_url):
    async with session.get(f"{base_url}/players") as r:
        data = await r.json()
        return data.get("players", [])


async def reset_players(session, base_url):
    async with session.delete(f"{base_url}/players") as r:
        return await r.json()


async def join_queue(session, base_url, player_id):
    async with session.post(f"{base_url}/queue/join", json={"player_id": player_id}) as r:
        return await r.json()


async def poll_for_match(session, base_url, player_id):
    deadline = asyncio.get_event_loop().time() + POLL_TIMEOUT_S
    while asyncio.get_event_loop().time() < deadline:
        async with session.get(f"{base_url}/match/{player_id}") as r:
            data = await r.json()
            if "match_id" in data:
                return data
        await asyncio.sleep(POLL_INTERVAL_S)
    return None


async def report_result(session, base_url, match_id, winner_id, loser_id):
    payload = {"match_id": match_id, "winner_id": winner_id, "loser_id": loser_id}
    async with session.post(f"{base_url}/match/result", json=payload) as r:
        return await r.json()


# --- Simulation ---

async def simulate_player(session, base_url, player, results):
    player_id = player["player_id"]
    player_elo = player.get("elo", player.get("initial_elo", 1200))

    join = await join_queue(session, base_url, player_id)
    if join.get("status") != "queued":
        return

    queue_time = asyncio.get_event_loop().time()
    match = await poll_for_match(session, base_url, player_id)
    if match is None:
        print(f"[TIMEOUT] {player_id[:8]} never matched")
        return

    wait_s = asyncio.get_event_loop().time() - queue_time

    match_id = match["match_id"]
    p1_id = match["player1_id"]
    p2_id = match["player2_id"]
    opponent_id = p2_id if p1_id == player_id else p1_id

    # Only player1 reports the result to avoid double-reporting
    if p1_id != player_id:
        return

    opponent_elo = results.get(opponent_id, {}).get("elo", 1200)
    prob = win_probability(player_elo, opponent_elo)
    winner_id = player_id if random.random() < prob else opponent_id
    loser_id = opponent_id if winner_id == player_id else player_id

    result = await report_result(session, base_url, match_id, winner_id, loser_id)
    print(
        f"[MATCH] {player_id[:8]} vs {opponent_id[:8]} | "
        f"winner: {winner_id[:8]} | "
        f"elos: {result.get('winner_new_elo')} / {result.get('loser_new_elo')} | "
        f"wait: {wait_s:.1f}s"
    )


async def run_round(session, base_url, players):
    elo_map = {p["player_id"]: p for p in players}
    tasks = [simulate_player(session, base_url, p, elo_map) for p in players]
    await asyncio.gather(*tasks)


async def main(args):
    base_url = args.url.rstrip("/")

    async with aiohttp.ClientSession() as session:
        # Setup
        if args.mode == "reset":
            print("Resetting ecosystem...")
            await reset_players(session, base_url)
            print(f"Creating {args.players} players...")
            new_players = [make_player() for _ in range(args.players)]
            await asyncio.gather(*[create_player(session, base_url, p) for p in new_players])
            players = new_players
        else:
            print("Loading existing players...")
            players = await get_players(session, base_url)
            if not players:
                print("No existing players found. Run with --mode reset first.")
                return

        print(f"Loaded {len(players)} players")

        for round_num in range(1, args.rounds + 1):
            print(f"\n--- Round {round_num}/{args.rounds} ({len(players)} players) ---")
            await run_round(session, base_url, players)

            # ~60% rejoin next round
            players = random.sample(players, k=int(len(players) * 0.6))

        print("\nSimulation complete.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Matchmaking simulation client")
    parser.add_argument("--mode", choices=["reset", "continue"], default="reset",
                        help="reset: clear and regenerate players | continue: reuse existing")
    parser.add_argument("--players", type=int, default=100,
                        help="Number of players to generate (reset mode only)")
    parser.add_argument("--rounds", type=int, default=5,
                        help="Number of simulation rounds")
    parser.add_argument("--url", default=DEFAULT_URL,
                        help="Base URL of the matchmaking service")
    args = parser.parse_args()
    asyncio.run(main(args))
