# Gemma4 Snake AI — Project Specification

## Overview

A Quarkus-based Snake game simulation in which a **LangChain4j AI agent** backed by Gemini `gemini-4-flash` drives the snake autonomously on a 30×30 grid. The application exposes a REST API to start and observe simulation runs; a Quarkus Scheduler drives the game automatically. Game state is forwarded to the model each turn via a LangChain4j AI service as a plain-text prompt; the response is parsed manually into a `Direction` enum. A browser-based UI renders the live game via WebSocket and provides a restart button.

---

## Technology Stack

| Concern | Choice |
|---|---|
| Runtime | Quarkus (latest) |
| Language | Java 26, virtual threads |
| Build | Maven (single module), Maven Wrapper (`./mvnw`) — always use `./mvnw`, never bare `mvn` |
| REST | Jakarta RESTful Web Services (JAX-RS) via `quarkus-rest` |
| JSON | Jackson (`quarkus-rest-jackson`) |
| AI model | Google Gemini `gemini-4-flash` |
| AI framework | LangChain4j (`quarkus-langchain4j-ai-gemini`) |
| Scheduling | Quarkus Scheduler (`quarkus-scheduler`) |
| WebSocket | Quarkus WebSockets Next (`quarkus-websockets-next`) |
| Frontend | HTML5 Canvas (vanilla JS, served as static resource) |
| Config | MicroProfile Config |
| Logging | `java.lang.System.Logger` |
| Testing | JUnit 5 + AssertJ |

---

## Game Rules

- Grid: **30 × 30** cells, origin `(0,0)` at top-left, `x` increases right, `y` increases down.
- The snake spawns at a **random position and direction**, with a margin that ensures it fits within the grid at the configured initial length.
### Food

| Rule | Detail |
|---|---|
| **Maximum** | Up to **5 food items** on the board at any time. |
| **Replenishment** | Each consumed item is replaced immediately by a new one, keeping the count at 5 whenever enough empty cells exist. |
| **Placement** | New food is placed at a randomly chosen empty cell (not occupied by the snake or another food item). |

### Snake behaviour

| Rule | Detail |
|---|---|
| **Continuous movement** | The snake advances one cell per tick in its current direction — it never stops. |
| **Growth** | When the head enters a food cell the snake grows by one segment; the tail does **not** vacate its cell that tick. |
| **Death — wall** | If the head would move outside the 30×30 boundary the game ends immediately (the move is not applied). |
| **Death — self** | If the head would enter any cell currently occupied by the snake's own body the game ends immediately. |

- **Score** = number of food items eaten during the run.

---

## Game Engine

| Rule | Detail |
|---|---|
| **Fixed interval** | The engine advances the game on a configurable fixed tick interval (default 200 ms), driven by Quarkus Scheduler (`@Scheduled(every = "${game.tick.interval:200ms}")`). |
| **Continuous updates** | Every tick the engine applies the latest known direction, updates positions, checks collisions, and resolves food events — regardless of agent activity. |
| **Latest-known decision** | The engine reads whichever direction the agent last provided (`AtomicReference<Direction> latestDirection` on `GameEngine`). If no new decision has arrived, the previous direction is reused (straight ahead). |
| **Non-blocking** | The engine never waits for an agent response. Agent calls are fired on a virtual-thread executor after each tick; when a response arrives it updates `latestDirection` atomically. |
| **Skipped ticks** | If a prior agent call is still in flight (`agentBusy` flag), no new call is dispatched that tick — the snake simply continues in its current direction. |

`GameEngine` is `@ApplicationScoped` and holds `AtomicReference<Direction> latestDirection` and `AtomicBoolean agentBusy`. A dedicated `ExecutorService` (`Executors.newVirtualThreadPerTaskExecutor()`) runs agent calls off the scheduler thread.

---

## Communication

| Rule | Detail |
|---|---|
| **Asynchronous** | All communication between the engine and the agent is non-blocking. The engine never waits for an agent response before advancing the game. |
| **Not every update** | The agent is not invoked on every tick. If an agent call is already in flight (`agentBusy == true`), the tick is skipped — no new request is dispatched. |
| **Fire-and-forget** | The agent receives a `RenderState` snapshot captured at dispatch time. No pending-state queue is maintained; if multiple ticks elapse during a slow model call, the intermediate states are simply not processed by the agent. |

### Async dispatch pattern

```
latestDirection: AtomicReference<Direction>   ← last parsed direction; null on restart
agentBusy:       AtomicBoolean                ← true while a Gemini call is in flight

On each tick (@Scheduled, virtual thread):
  gameState.applyDirection(latestDirection.get())
  gameState.advance()
  state = gameState.toRenderState()
  broadcaster.broadcast(serialize(state))
  if snake alive AND agentBusy.compareAndSet(false, true):
      AGENT_EXECUTOR.execute(() -> decideAsync(state))

decideAsync(state):
  try:
      raw = snakeAgent.decide(buildPrompt(state))
      latestDirection.set(Direction.parse(raw))
  finally:
      agentBusy.set(false)
```

---

## AI Agent Design

The agent is consulted asynchronously and only when free. Its direction response is applied by the engine on the next available tick.

### LangChain4j AI service

The agent is a LangChain4j `@RegisterAiService @ApplicationScoped` interface. LangChain4j handles HTTP transport to Gemini; the prompt is constructed manually by `GameEngine.buildPrompt(RenderState)` and passed as a single string argument. The model returns a plain-text word; it is parsed via `Direction.parse(raw)` (`trim().toUpperCase()` + `valueOf`). On any exception the current direction is kept unchanged.

```java
@RegisterAiService
@ApplicationScoped
public interface SnakeAgent {

    @SystemMessage("""
        You are controlling a snake in a grid-based game.
        Each turn you receive the game state with every move pre-evaluated as safe or lethal.
        Never choose a move marked WALL or BODY — the snake dies immediately.
        Prefer moves with more steps to the nearest wall.
        Move toward visible food when it is safe to do so.
        Respond with exactly one word: UP DOWN LEFT RIGHT.
        """)
    String decide(@UserMessage String stateDescription);
}
```

The `stateDescription` built by `GameEngine.buildPrompt(RenderState)` contains:
- Grid dimensions, coordinate convention, and current snake direction.
- Head position.
- All four candidate moves with a safety label (`safe` / `WALL — instant death` / `BODY — instant death`) and steps-to-wall distance.
- Food positions.

---

## Architecture — BCE/ECB

Top-level package: `dev.denisarruda.gemmasnakeai`

```
dev.denisarruda.gemmasnakeai
├── agent                     ← AI agent BC
│   └── boundary
│       └── SnakeAgent        (@RegisterAiService @ApplicationScoped: LangChain4j interface — @SystemMessage + @UserMessage String decide(), returns String; GameEngine parses to Direction)
│
├── game                      ← game domain BC
│   ├── entity
│   │   ├── Direction         (enum: UP, DOWN, LEFT, RIGHT — static parse() method)
│   │   ├── Snake             (mutable entity: ArrayList of Positions, direction, alive flag, score)
│   │   ├── GameState         (@ApplicationScoped mutable state: snake, food list, tick counter)
│   │   └── RenderState       (immutable record snapshot: tick, List<SnakeRender>, List<Position>)
│   └── control
│       └── GameEngine        (@ApplicationScoped: @Scheduled tick loop, prompt builder, async agent dispatch)
│
└── simulation                ← I/O boundary BC
    └── boundary
        ├── GameBroadcaster   (@ApplicationScoped: WebSocketConnection registry + broadcast)
        ├── GameResource      (JAX-RS @Path("/game"): POST /game/restart)
        └── GameSocket        (@WebSocket("/ws/game"): registers/unregisters connections via GameBroadcaster)
```

### Key design decisions

- `GameState` is `@ApplicationScoped` and holds a single mutable game. `GameEngine` calls `gameState.toRenderState()` each tick to produce an immutable `RenderState` snapshot for broadcast and prompt-building.
- `SnakeAgent` is a LangChain4j AI service interface (`@RegisterAiService @ApplicationScoped`). LangChain4j owns HTTP transport to Gemini. The prompt is built manually in `GameEngine.buildPrompt()` and passed as a single `@UserMessage String`.
- `GameBroadcaster` decouples the broadcast concern from the WebSocket lifecycle: `GameSocket` manages connection registration; `GameEngine` calls `broadcaster.broadcast()` without knowing about WebSocket details.
- All JSON serialization happens in the boundary layer only.

---

## REST API

Base path: `/game`

| Method | Path | Body | Response | Description |
|---|---|---|---|---|
| `POST` | `/game/restart` | — | `204 No Content` | Reset the game: clears direction, resets state, spawns a new random snake. |

The scheduler drives the game automatically — there are no manual step endpoints.

---

## Web Interface

### Rendering

| Concern | Detail |
|---|---|
| **Canvas** | `600 × 600 px` HTML5 Canvas (30 cells × 20 px/cell). |
| **Grid lines** | Thin dark lines drawn each frame (`#1a1a3a`). |
| **Snake** | Head cell brighter (`#80ffb8`), body semi-transparent (`#00ff88`, alpha 0.7). Dead snake renders grey (`#444`). |
| **Food** | Filled red circles (`#ff6060`) centred in each cell. |
| **HUD** | Connection status dot (green/red), tick counter, score. |
| **Restart button** | Calls `POST /game/restart` via `fetch`. The WebSocket connection is unaffected and continues receiving ticks for the new game. |
| **Auto-restart** | If all snakes are dead after a render frame, the client calls `restart()` after a 2 s delay. |
| **Served from** | `src/main/resources/META-INF/resources/index.html` (single file, no build step). |

### WebSocket communication

| Concern | Detail |
|---|---|
| **Endpoint** | `ws://{host}/ws/game` |
| **Direction** | Server → client only. The engine broadcasts a `RenderState` JSON message on every tick. |
| **Reconnect** | Client reconnects automatically after 2 s on close. |

### Render-state JSON (server → client, every tick)

```json
{
  "tick": 42,
  "snakes": [
    {
      "agentId":   "agent-1",
      "cells":     [{"x": 15, "y": 15}, {"x": 14, "y": 15}, {"x": 13, "y": 15}],
      "direction": "RIGHT",
      "alive":     true
    }
  ],
  "foods": [
    {"x": 7, "y": 22},
    {"x": 3, "y": 5}
  ]
}
```

`cells[0]` is the head. `score` is not included in the broadcast payload.

---

## Configuration

All keys use MicroProfile Config (`application.properties`). The API key is supplied via environment variable `LLM_API_KEY`.

| Key | Default | Description |
|---|---|---|
| `quarkus.langchain4j.ai.gemini.api-key` | `${LLM_API_KEY}` | Google API key for Gemini — set via env var `LLM_API_KEY` |
| `quarkus.langchain4j.ai.gemini.model-name` | `gemini-4-flash` | Gemini model ID |
| `game.grid.size` | `30` | Board side length (square) |
| `game.tick.interval` | `200ms` | Fixed interval between engine ticks (`@Scheduled(every = "${game.tick.interval:200ms}")`) |
| `game.food.max` | `5` | Maximum simultaneous food items |
| `game.snake.initial-length` | `3` | Snake length at spawn |

---

## Package-info JavaDoc requirements

`package-info.java` is required for:
- `dev.denisarruda.gemmasnakeai` — top-level intent: AI-driven snake simulation
- `dev.denisarruda.gemmasnakeai.agent` — design decision: why prompts are plain text rather than function-calling

---

## Data Flow per Tick

```
[Quarkus Scheduler — every 200 ms, virtual thread]
  │
  GameEngine.tick()
    ├─ 1. gameState.applyDirection(latestDirection.get())     ← AtomicReference read; null = keep current direction
    ├─ 2. gameState.advance()                                 → move, reversal guard, collision check, food resolution
    ├─ 3. state = gameState.toRenderState()                   ← immutable RenderState snapshot
    ├─ 4. broadcaster.broadcast(serialize(state))             ← push JSON to all WS clients
    └─ 5. if snake alive AND agentBusy.compareAndSet(false, true):
              AGENT_EXECUTOR.execute(() -> decideAsync(state))
           else: skip (agent busy or game over)

decideAsync(state) — virtual thread:
    ├─ a. prompt = buildPrompt(state)                         ← head, 4 moves with safety + wall-distance, food
    ├─ b. raw    = snakeAgent.decide(prompt)                  ← Gemini HTTP call
    ├─ c. latestDirection.set(Direction.parse(raw))
    └─ d. agentBusy.set(false)   [always, in finally block]
```

---

## Out of Scope (v1)

- Persistent storage (in-memory only)
- Multiple concurrent games
- Training / fine-tuning
- Wrapping edges (walls are fatal)
