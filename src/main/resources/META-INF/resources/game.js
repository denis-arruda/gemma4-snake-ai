const GRID = 30;
const CELL = 20;

const canvas  = document.getElementById('game');
const ctx     = canvas.getContext('2d');
const statusEl = document.getElementById('status');
const scoreEl  = document.getElementById('score');

const agentColors = new Map();

function hslForAgent(agentId) {
    if (agentColors.has(agentId)) return agentColors.get(agentId);
    let hash = 0;
    for (const ch of agentId) hash = (hash * 31 + ch.charCodeAt(0)) | 0;
    const hue = Math.abs(hash) % 360;
    const color = `hsl(${hue}, 70%, 55%)`;
    agentColors.set(agentId, color);
    return color;
}

function headColor(agentId) {
    let hash = 0;
    for (const ch of agentId) hash = (hash * 31 + ch.charCodeAt(0)) | 0;
    const hue = Math.abs(hash) % 360;
    return `hsl(${hue}, 90%, 75%)`;
}

function clearCanvas() {
    ctx.fillStyle = '#0f0f23';
    ctx.fillRect(0, 0, canvas.width, canvas.height);

    ctx.strokeStyle = '#1e1e3a';
    ctx.lineWidth = 0.5;
    for (let i = 0; i <= GRID; i++) {
        ctx.beginPath();
        ctx.moveTo(i * CELL, 0);
        ctx.lineTo(i * CELL, GRID * CELL);
        ctx.stroke();
        ctx.beginPath();
        ctx.moveTo(0, i * CELL);
        ctx.lineTo(GRID * CELL, i * CELL);
        ctx.stroke();
    }
}

function drawFood(food) {
    ctx.fillStyle = '#e63946';
    for (const f of food) {
        const cx = f.x * CELL + CELL / 2;
        const cy = f.y * CELL + CELL / 2;
        ctx.beginPath();
        ctx.arc(cx, cy, CELL / 2 - 2, 0, Math.PI * 2);
        ctx.fill();
    }
}

function drawSnake(snake) {
    const color     = hslForAgent(snake.agentId);
    const headClr   = headColor(snake.agentId);
    const body      = snake.body;

    // Draw body segments (skip head)
    ctx.fillStyle = color;
    for (let i = 1; i < body.length; i++) {
        ctx.fillRect(body[i].x * CELL + 1, body[i].y * CELL + 1, CELL - 2, CELL - 2);
    }

    // Draw head
    ctx.fillStyle = headClr;
    ctx.fillRect(body[0].x * CELL + 1, body[0].y * CELL + 1, CELL - 2, CELL - 2);
}

function render(state) {
    clearCanvas();
    drawFood(state.food);
    for (const snake of state.snakes) {
        drawSnake(snake);
    }

    const alive = state.snakes.some(s => s.alive);
    statusEl.textContent = alive ? `Running — tick ${state.tick}` : 'Game Over';
    const totalScore = state.snakes.reduce((sum, s) => sum + s.score, 0);
    scoreEl.textContent = `Score: ${totalScore}`;
}

let ws;

function connect() {
    ws = new WebSocket(`ws://${location.host}/ws/game`);
    ws.onmessage = e => render(JSON.parse(e.data));
    ws.onclose   = () => setTimeout(connect, 2000);
    ws.onerror   = () => ws.close();
}

document.getElementById('restart').addEventListener('click', () => {
    fetch('/simulations/restart', { method: 'POST' })
        .catch(err => console.error('Restart failed', err));
});

connect();
