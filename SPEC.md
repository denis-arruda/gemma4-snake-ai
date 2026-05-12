# Gemma4 Snake AI — Project Specification

## Overview

A Quarkus-based Snake game simulation in which a **LangChain4j AI agent** backed by Gemini `gemini-4-flash` drives the snake autonomously on a 30×30 grid. The application exposes a REST API to start and observe simulation runs; a Quarkus Scheduler drives the game automatically. Game state is forwarded to the model each turn via a LangChain4j AI service as a plain-text prompt; the response is parsed manually into a `Direction` enum. A browser-based UI renders the live game via WebSocket and provides a restart button.

---

## Technology Stack

| Concern | Choice |
|---|---|
| Runtime | Quarkus (latest) |
| Language | Java 26 |
| Build | Maven (single module), Maven Wrapper (`./mvnw`) — always use `./mvnw`, never bare `mvn` |
| REST | Jakarta RESTful Web Services (JAX-RS) |
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

- Grid: **30 × 30** cells, origin `(0,0)` at top-left.
- The snake starts with length 3 at the centre, heading RIGHT.
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
| **Fixed interval** | The engine advances the game on a configurable fixed tick interval (default 200 ms), driven by Quarkus Scheduler. |
| **Continuous updates** | Every tick the engine applies the latest known direction, updates positions, checks collisions, and resolves food events — regardless of agent activity. |
| **Latest-known decision** | The engine reads whichever direction the agent last provided. If no new decision has arrived since the previous tick, the previous direction is reused (straight ahead). |
| **Non-blocking** | The engine never waits for an agent response. Agent calls are fired asynchronously; when a response arrives it updates the stored direction atomically. The tick loop is never stalled by Gemini latency. |

Agent calls are dispatched in a separate thread after each tick using `CompletableFuture.runAsync`. The stored direction is held in an `AtomicReference<Direction>` on `GameSession`.

---

## Communication

| Rule | Detail |
|---|---|
| **Asynchronous** | All communication between the engine and the agent is non-blocking. The engine never waits for an agent response before advancing the game. |
| **Not every update** | The agent is not notified on every tick. If an agent call is already in flight, the tick is skipped — no new request is dispatched. |
| **Latest state only** | Game state is held in a single-slot `AtomicReference<GameState>`. Each tick overwrites any pending (unprocessed) state. When the agent becomes free it reads whichever state is current, discarding anything stale. |

### Single-slot mailbox pattern

```
pendingState: AtomicReference<GameState>   ← overwritten every tick (latest wins)
agentBusy:    AtomicBoolean                ← true while a Gemini call is in flight

On each tick:
  pendingState.set(latestSnapshot)
  if agentBusy.compareAndSet(false, true):
      dispatch async agent call            ← only if agent is free

On agent completion:
  latestDirection.set(parsedDirection)
  next = pendingState.getAndSet(null)
  if next != null:
      dispatch async agent call            ← consume accumulated latest state immediately
  else:
      agentBusy.set(false)                 ← release; next tick will re-engage if needed
```

---

## AI Agent Design

The agent is consulted asynchronously and only when free. It always processes the most recent game state — never a queued or stale one. Its direction response is applied by the engine on the next available tick.

### LangChain4j AI service

The agent is a LangChain4j `@RegisterAiService @ApplicationScoped` interface. LangChain4j handles HTTP transport to Gemini; the prompt is constructed manually by `SimulationRunner` and passed as a single string argument. The model returns a plain-text word; `SimulationRunner.callAgent()` parses it via `Direction.valueOf(response.trim().toUpperCase())`. On any exception the caller falls back to the **current direction** (straight ahead).

```java
@RegisterAiService
@ApplicationScoped
public interface SnakeAgent {

    @SystemMessage("""
        You are controlling a snake on a 30x30 grid.
        Grid origin (0,0) is top-left. X increases right, Y increases down.
        Respond with exactly one word: UP DOWN LEFT RIGHT.
        """)
    String decide(@UserMessage String stateDescription);
}
```

The `stateDescription` is a plain-text string built by `SimulationRunner.buildPrompt(GameState)` containing the head position, body, food positions, current direction, score, and step number.

---

## Architecture — BCE/ECB

Top-level package: `dev.denisarruda.gemmasnakeai`

```
dev.denisarruda.gemmasnakeai
├── game                      ← game domain BC
│   ├── entity
│   │   ├── Direction         (enum: UP, DOWN, LEFT, RIGHT — carries Δx/Δy)
│   │   ├── Cell              (record: int x, int y)
│   │   ├── Snake             (entity: deque of cells, direction, grew flag)
│   │   ├── Food              (record: Cell position)
│   │   └── GameState         (record: snapshot — snake, food, score, step, status)
│   ├── control
│   │   ├── GameEngine        (interface: static tick, isCollision, nextHead logic)
│   │   └── FoodPlacer        (interface: static random-empty-cell logic)
│   └── boundary
│       └── GameSession       (@ApplicationScoped: mutable game lifecycle; holds AtomicReference<Direction> latestDirection, AtomicReference<GameState> pendingState, AtomicBoolean agentBusy)
│
├── agent                     ← AI agent BC
│   └── boundary
│       └── SnakeAgent        (@RegisterAiService @ApplicationScoped: LangChain4j interface — @SystemMessage + @UserMessage String decide(), returns String; caller parses to Direction)
│
└── simulation                ← orchestration BC
    ├── entity
    │   ├── SimulationRun     (record: id, list of GameState snapshots, final score)
    │   └── SimulationStatus  (enum: RUNNING, COMPLETED, ERROR)
    ├── control
    │   └── SimulationRunner  (@Scheduled: fixed-interval tick — applies latest direction, fires async agent call, records snapshot)
    ├── boundary
    │   ├── SimulationsResource  (JAX-RS: POST /simulations, GET /simulations/{id})
    │   └── GameSocket           (@WebSocket("/ws/game"): broadcasts render-state JSON on every tick)
```

### Key design decisions

- `GameEngine` and `FoodPlacer` are **interfaces with only static methods** — no instances, no CDI beans.
- `GameSession` is `@ApplicationScoped` and holds a single mutable game; a simulation run clones snapshots into an immutable `SimulationRun` record after each step.
- `SnakeAgent` is a LangChain4j AI service interface (`@RegisterAiService @ApplicationScoped`). LangChain4j owns HTTP transport to Gemini. The prompt is built manually in `SimulationRunner.buildPrompt()` and passed as a single `@UserMessage String`; the raw `String` response is parsed by `Direction.valueOf(response.trim().toUpperCase())`.
- All JSON mapping happens in the boundary layer only.

---

## REST API

Base path: `/simulations`

| Method | Path | Description |
|---|---|---|
| `POST` | `/simulations` | Start a new simulation run. Returns run ID and initial state. |
| `GET` | `/simulations/{id}` | Retrieve a completed or in-progress run (all snapshots). |
| `POST` | `/simulations/restart` | Stop the current run (if any) and start a fresh one. Returns the new run's initial state. Used by the UI restart button. |

The scheduler drives the game automatically — there are no manual step or run-to-completion endpoints.

All responses use `application/json`. Request bodies (where present) use `application/json`.

### Sample response — `POST /simulations`

```json
{
  "id": "a1b2c3",
  "status": "RUNNING",
  "step": 0,
  "score": 0,
  "snake": {
    "head": { "x": 15, "y": 15 },
    "body": [
      { "x": 15, "y": 15 },
      { "x": 14, "y": 15 },
      { "x": 13, "y": 15 }
    ],
    "direction": "RIGHT"
  },
  "food": { "x": 7, "y": 22 }
}
```

---

## Web Interface

### Rendering

| Concern | Detail |
|---|---|
| **Canvas** | HTML5 Canvas element sized to the 30×30 grid. Each cell maps to a fixed pixel block. |
| **Snake colour** | Each snake is rendered in a distinct colour deterministically keyed to its `agentId` (e.g. HSL hue derived from a hash of the id). |
| **Food markers** | Food items are rendered as distinct markers (e.g. filled circles) visually differentiated from snake segments. |
| **Restart button** | A "Restart" button calls `POST /simulations/restart` via `fetch`. On success the canvas resets and the existing WebSocket connection continues receiving the new run's ticks immediately. |
| **Served from** | Static files (`index.html`, `game.js`) served by Quarkus from `src/main/resources/META-INF/resources`. |

### WebSocket communication

| Concern | Detail |
|---|---|
| **Endpoint** | Single WebSocket endpoint at `/ws/game`. |
| **Direction** | Server → client only. The engine broadcasts a render-state JSON message on every tick. |
| **Payload** | See render-state schema below. |

### Render-state JSON (server → client, every tick)

```json
{
  "tick": 42,
  "status": "RUNNING",
  "snakes": [
    {
      "agentId": "agent-1",
      "head": { "x": 15, "y": 15 },
      "body": [{ "x": 15, "y": 15 }, { "x": 14, "y": 15 }, { "x": 13, "y": 15 }],
      "direction": "RIGHT",
      "score": 3,
      "alive": true
    }
  ],
  "food": [
    { "x": 7, "y": 22 },
    { "x": 3, "y": 5 }
  ]
}
```

---

## Configuration

All keys use MicroProfile Config (`application.properties`). The API key is supplied via the environment variable `LLM_API_KEY`, which MicroProfile Config maps automatically to the property below.

| Key | Default | Description |
|---|---|---|
| `quarkus.langchain4j.google-ai-gemini.api-key` | `${LLM_API_KEY}` | Google API key for Gemini — set via env var `LLM_API_KEY` |
| `quarkus.langchain4j.google-ai-gemini.model-name` | `gemini-4-flash` | Gemini model ID |
| `game.grid.size` | `30` | Board side length (square) |
| `simulation.max-steps` | `5000` | Hard cap on steps per run to avoid infinite loops (declared; not yet enforced in v1) |
| `simulation.tick-interval` | `200ms` | Fixed interval between engine ticks (`@Scheduled(every = "${simulation.tick-interval:200ms}")`) |

---

## Package-info JavaDoc requirements

`package-info.java` is required for:
- `dev.denisarruda.gemmasnakeai` — top-level intent: AI-driven snake simulation
- `dev.denisarruda.gemmasnakeai.agent` — design decision: why prompts are plain text rather than function-calling

---

## Data Flow per Tick

The tick loop and the agent are fully decoupled via the single-slot mailbox on `GameSession`.

```
[Quarkus Scheduler — every 200 ms]
  │
  SimulationRunner.tick()
    ├─ 1. direction = GameSession.latestDirection()           ← AtomicReference read, never blocks
    ├─ 2. state    = GameEngine.advance(current, direction)   → move, collision, food resolution
    ├─ 3. GameSession.record(state)
    ├─ 4. GameSocket.broadcast(renderState(state))            ← push to all WS clients, non-blocking
    ├─ 5. if game over → stop scheduler, return
    ├─ 6. GameSession.pendingState.set(state)                 ← overwrite; old state dropped
    └─ 7. if GameSession.agentBusy.compareAndSet(false, true) → dispatch async agent call (*)
           else → skip (agent already processing a more recent state)

(*) Async agent call (background thread):
    ├─ a. state  = GameSession.pendingState.getAndSet(null)   ← consume latest
    ├─ b. dir    = callAgent(state)   ← builds prompt, calls SnakeAgent.decide(prompt), parses String → Direction
    ├─ c. GameSession.latestDirection.set(dir)
    ├─ d. next   = GameSession.pendingState.getAndSet(null)   ← any state arrived while busy?
    └─ e. if next != null → loop back to (b) with next       ← process immediately
           else           → GameSession.agentBusy.set(false) ← release lock
```

---

## Out of Scope (v1)

- Persistent storage (in-memory only)
- Multiple concurrent games
- Training / fine-tuning
- Wrapping edges (walls are fatal)
