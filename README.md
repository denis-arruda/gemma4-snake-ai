# Gemma4 Snake AI

> A Snake game where **Google Gemma 4** plays against itself — autonomously, in real time, powered by Quarkus.

---

## What is this?

This project puts **Gemini `gemma-4-flash`** — Google's latest and most capable Gemma 4 generation model — in the driver's seat of a classic Snake game. Every 200 ms the game engine snapshots the board, builds a structured prompt describing every possible move with pre-computed safety labels and wall distances, and fires it off to Gemma 4. The model reads the situation, reasons about the best move, and replies with a single word. The snake follows.

No hard-coded heuristics. No pathfinding algorithm. Just Gemma 4 playing Snake.

Watch it live in your browser: the board updates in real time over WebSocket, showing the snake navigating the grid, chasing food, and — occasionally — making surprisingly clever turns.

For full technical detail see [SPEC.md](SPEC.md).

---

## Why Gemma 4?

Gemma 4 (`gemini-4-flash`) represents a step change in Google's frontier reasoning models. Its low latency and high instruction-following accuracy make it uniquely suited for this kind of tight feedback loop: the model must process a structured game state, weigh safety constraints, and commit to a single word — all within a fraction of the 200 ms tick window. Larger or slower models would stall the game; weaker models would ignore the safety labels and die immediately. Gemma 4 hits the sweet spot.

---

## Requirements

| Requirement | Version |
|---|---|
| Java | 26+ |
| Maven Wrapper | included (`./mvnw`) |
| Google Gemini API key | [aistudio.google.com](https://aistudio.google.com) |

Set your API key as an environment variable before running:

```bash
export LLM_API_KEY=your_api_key_here
```

---

## Build

```bash
./mvnw package
```

Run tests only:

```bash
./mvnw test
```

---

## Run

### Development mode (live reload)

```bash
./mvnw quarkus:dev
```

### Production (after build)

```bash
java -jar target/quarkus-app/quarkus-run.jar
```

Then open [http://localhost:8080](http://localhost:8080) in your browser.

---

## How it works (quick summary)

1. **Quarkus Scheduler** fires a tick every 200 ms on a virtual thread.
2. The engine applies the latest AI direction, advances the snake, and checks for collisions and food.
3. A `RenderState` snapshot is serialised to JSON and broadcast to all connected browsers over WebSocket.
4. If the snake is alive and no AI call is in flight, the engine dispatches an async call to **Gemma 4** via LangChain4j, passing a prompt that lists all four moves with safety labels (`safe` / `WALL — instant death` / `BODY — instant death`) and distance to each wall.
5. Gemma 4 replies with `UP`, `DOWN`, `LEFT`, or `RIGHT`. The engine stores the direction and applies it on the next tick.
6. On death the browser auto-restarts the game after 2 seconds. You can also hit **Restart** at any time.

The AI call is fully non-blocking — the game never stalls waiting for a model response. If Gemma 4 is still thinking when the next tick fires, the snake keeps going straight.

---

## Project structure

```
src/main/java/dev/denisarruda/gemmasnakeai/
├── agent/boundary/       SnakeAgent      — LangChain4j AI service (Gemma 4)
├── game/entity/          Direction, Snake, GameState, RenderState
├── game/control/         GameEngine      — tick loop & prompt builder
└── simulation/boundary/  GameResource, GameSocket, GameBroadcaster

src/main/resources/META-INF/resources/
└── index.html            — browser UI (Canvas + WebSocket client)
```

See [SPEC.md](SPEC.md) for the full architecture, data model, API reference, and configuration options.
