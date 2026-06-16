const express = require('express');
const bcrypt = require('bcrypt');
const jwt = require('jsonwebtoken');
const { v4: uuidv4 } = require('uuid');
const fs = require('fs');
const path = require('path');
const cors = require('cors');

let Chess;
try {
  Chess = require('chess.js').Chess;
} catch (e) {
  Chess = require('chess.js');
}

const app = express();
app.use(express.json());
app.use(cors());

const PORT = 3000;
const JWT_SECRET = 'chessomania_secret_jwt_key_2026_production_safe';
const PUBLIC_DIR = path.join(__dirname, 'public');
const DATA_DIR = path.join(__dirname, 'data');

// Ensure data directory exists
if (!fs.existsSync(DATA_DIR)) {
  fs.mkdirSync(DATA_DIR);
}

// ── DATA STORAGE LAYER (Atomic writes) ──────────────────────
const cache = {};

function readData(name) {
  if (cache[name]) return cache[name];
  const file = path.join(DATA_DIR, `${name}.json`);
  try {
    if (fs.existsSync(file)) {
      const raw = fs.readFileSync(file, 'utf8');
      cache[name] = JSON.parse(raw);
      return cache[name];
    }
  } catch (e) {
    console.error(`Error reading ${name}.json:`, e);
  }
  cache[name] = {};
  return cache[name];
}

function writeData(name, data) {
  const file = path.join(DATA_DIR, `${name}.json`);
  const tmp = `${file}.tmp`;
  try {
    fs.writeFileSync(tmp, JSON.stringify(data, null, 2), 'utf8');
    fs.renameSync(tmp, file);
    cache[name] = data;
  } catch (e) {
    console.error(`Error writing ${name}.json atomically:`, e);
  }
}

// ── SSE CONNECTION BROKER ────────────────────────────────────
// Map: username -> { res: Response, heartbeatInterval: Timer }
const sseConnections = new Map();
// Map: username -> gameId (active game)
const playerGameMap = new Map();
// Map: username -> list of offline events
const offlineQueues = new Map();

function sendSseEvent(res, data) {
  res.write(`data: ${JSON.stringify(data)}\n\n`);
}

function pushEvent(username, data) {
  const conn = sseConnections.get(username);
  if (conn) {
    try {
      sendSseEvent(conn.res, data);
    } catch (e) {
      closeSseForUser(username);
    }
  } else {
    // Queue event for offline user
    if (!offlineQueues.has(username)) {
      offlineQueues.set(username, []);
    }
    const q = offlineQueues.get(username);
    q.push(data);
    offlineQueues.set(username, q.slice(-50)); // Limit to last 50 events
  }
}

function closeSseForUser(username) {
  const conn = sseConnections.get(username);
  if (conn) {
    clearInterval(conn.heartbeatInterval);
    try { conn.res.end(); } catch (e) { }
    sseConnections.delete(username);
  }
}

function getOnlineStatus(username) {
  if (!sseConnections.has(username)) return 'offline';
  return playerGameMap.has(username) ? 'in_game' : 'online';
}

function broadcastToFriends(username, event) {
  const friends = readData('friends');
  const myFriends = (friends[username] || []).filter(f => f.status === 'accepted');
  myFriends.forEach(f => pushEvent(f.username, event));
}

function setInGame(username, gameId) {
  playerGameMap.set(username, gameId);
  broadcastToFriends(username, { type: 'friend_status', username, status: 'in_game' });
}

function clearInGame(username) {
  playerGameMap.delete(username);
  if (sseConnections.has(username)) {
    broadcastToFriends(username, { type: 'friend_status', username, status: 'online' });
  }
}

// ── MOVE VALIDATION ──────────────────────────────────────────
function validateMove(fen, from, to, promotion) {
  try {
    const chess = (fen === 'startpos' || !fen) ? new Chess() : new Chess(fen);
    const result = chess.move({
      from,
      to,
      promotion: promotion || undefined
    });

    if (!result) {
      return { valid: false, reason: 'Illegal move' };
    }

    return {
      valid: true,
      newFen: chess.fen(),
      isCheckmate: chess.isCheckmate ? chess.isCheckmate() : chess.in_checkmate(),
      isStalemate: chess.isStalemate ? chess.isStalemate() : chess.in_stalemate(),
      isDraw: chess.isDraw ? chess.isDraw() : (chess.in_draw ? chess.in_draw() : chess.draw()),
      isCheck: chess.isCheck ? chess.isCheck() : chess.in_check(),
      san: result.san
    };
  } catch (e) {
    console.error('Move validation error:', e);
    return { valid: false, reason: 'Invalid board configuration or format' };
  }
}

// ── ABANDONMENT TIMER ─────────────────────────────────────────
const ABANDON_TIMEOUT_MS = 5 * 60 * 1000; // 5 minutes

function scheduleAbandonCheck(gameId) {
  setTimeout(() => {
    const data = readData('games');
    const game = (data.active || {})[gameId];
    if (!game || game.status !== 'active') return;

    const timeSinceLastMove = Date.now() - game.lastMoveAt;
    if (timeSinceLastMove >= ABANDON_TIMEOUT_MS) {
      const isWhiteTurn = game.moves.length % 2 === 0;
      const abandoner = isWhiteTurn ? game.white : game.black;
      const winner = isWhiteTurn ? game.black : game.white;

      game.status = 'abandoned';
      game.winner = winner;
      clearInGame(game.white);
      clearInGame(game.black);
      writeData('games', data);

      const endEvent = { type: 'game_ended', gameId, status: 'abandoned', winner, loser: abandoner };
      pushEvent(game.white, endEvent);
      pushEvent(game.black, endEvent);
    } else {
      scheduleAbandonCheck(gameId);
    }
  }, ABANDON_TIMEOUT_MS);
}

// ── AUTH MIDDLEWARE ──────────────────────────────────────────
function requireAuth(req, res, next) {
  const authHeader = req.headers['authorization'];
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return res.status(401).json({ error: 'No authorization token provided' });
  }
  const token = authHeader.substring(7);
  try {
    req.user = jwt.verify(token, JWT_SECRET);
    next();
  } catch (e) {
    res.status(401).json({ error: 'Invalid or expired authorization token' });
  }
}

// ════════════ ROUTE HANDLERS ═════════════════════════════════

// ── Auth Endpoints ───────────────────────────────────────────
app.post('/api/register', async (req, res) => {
  const { username, password } = req.body;
  if (!username || !password) return res.status(400).json({ error: 'Username and password required' });
  if (username.length < 3 || username.length > 20) return res.status(400).json({ error: 'Username must be 3-20 characters' });
  if (password.length < 6) return res.status(400).json({ error: 'Password must be at least 6 characters' });
  if (!/^[a-zA-Z0-9_]+$/.test(username)) return res.status(400).json({ error: 'Username can only contain alphanumeric characters and underscores' });

  const users = readData('users');
  if (users[username.toLowerCase()]) return res.status(409).json({ error: 'Username already taken' });

  const passwordHash = await bcrypt.hash(password, 10);
  const normalizedUsername = username.toLowerCase();
  users[normalizedUsername] = {
    id: uuidv4(),
    username, // Keep original casing for display
    passwordHash,
    createdAt: Date.now()
  };
  writeData('users', users);

  const token = jwt.sign({ userId: users[normalizedUsername].id, username }, JWT_SECRET, { expiresIn: '7d' });
  res.json({ token, username });
});

app.post('/api/login', async (req, res) => {
  const { username, password } = req.body;
  if (!username || !password) return res.status(400).json({ error: 'Username and password required' });

  const users = readData('users');
  const user = users[username.toLowerCase()];
  if (!user) return res.status(401).json({ error: 'Invalid credentials' });

  const match = await bcrypt.compare(password, user.passwordHash);
  if (!match) return res.status(401).json({ error: 'Invalid credentials' });

  const token = jwt.sign({ userId: user.id, username: user.username }, JWT_SECRET, { expiresIn: '7d' });
  res.json({ token, username: user.username });
});

app.post('/api/logout', requireAuth, (req, res) => {
  const { username } = req.user;
  closeSseForUser(username);
  broadcastToFriends(username, { type: 'friend_status', username, status: 'offline' });
  res.json({ success: true });
});

// ── Friends Endpoints ────────────────────────────────────────
app.get('/api/friends/list', requireAuth, (req, res) => {
  const { username } = req.user;
  const friends = readData('friends');
  const myFriendsList = friends[username.toLowerCase()] || [];

  const formattedFriends = myFriendsList
    .filter(f => f.status === 'accepted')
    .map(f => ({
      username: f.username,
      status: getOnlineStatus(f.username.toLowerCase())
    }));
  res.json({ friends: formattedFriends });
});

app.get('/api/friends/pending', requireAuth, (req, res) => {
  const { username } = req.user;
  const friends = readData('friends');
  const myFriendsList = friends[username.toLowerCase()] || [];
  const pending = myFriendsList.filter(f => f.status === 'pending_incoming');
  res.json({ pending });
});

app.post('/api/friends/request', requireAuth, (req, res) => {
  const { username } = req.user;
  const { targetUsername } = req.body;
  if (!targetUsername) return res.status(400).json({ error: 'Target username is required' });
  if (username.toLowerCase() === targetUsername.toLowerCase()) return res.status(400).json({ error: 'Cannot add yourself as friend' });

  const users = readData('users');
  const targetUser = users[targetUsername.toLowerCase()];
  if (!targetUser) return res.status(404).json({ error: 'User not found' });

  const friends = readData('friends');
  const myKey = username.toLowerCase();
  const targetKey = targetUsername.toLowerCase();

  friends[myKey] = friends[myKey] || [];
  friends[targetKey] = friends[targetKey] || [];

  const alreadyExists = friends[myKey].some(f => f.username.toLowerCase() === targetKey);
  if (alreadyExists) return res.status(409).json({ error: 'Relationship or request already exists' });

  // Add outgoing request to sender, incoming to recipient
  friends[myKey].push({ username: targetUser.username, status: 'pending_outgoing' });
  friends[targetKey].push({ username, status: 'pending_incoming' });
  writeData('friends', friends);

  pushEvent(targetUser.username, { type: 'friend_request', from: username });
  res.json({ success: true });
});

app.post('/api/friends/respond', requireAuth, (req, res) => {
  const { username } = req.user;
  const { fromUsername, accept } = req.body;
  if (!fromUsername) return res.status(400).json({ error: 'Sender username is required' });

  const friends = readData('friends');
  const myKey = username.toLowerCase();
  const fromKey = fromUsername.toLowerCase();

  const myEntry = (friends[myKey] || []).find(f => f.username.toLowerCase() === fromKey && f.status === 'pending_incoming');
  if (!myEntry) return res.status(404).json({ error: 'Request not found' });

  if (accept) {
    myEntry.status = 'accepted';
    const theirEntry = (friends[fromKey] || []).find(f => f.username.toLowerCase() === myKey);
    if (theirEntry) theirEntry.status = 'accepted';
    writeData('friends', friends);

    pushEvent(myEntry.username, { type: 'friend_accepted', by: username });
    pushEvent(username, { type: 'friend_status', username: myEntry.username, status: getOnlineStatus(fromKey) });
  } else {
    // Decline: remove entries
    friends[myKey] = friends[myKey].filter(f => f.username.toLowerCase() !== fromKey);
    friends[fromKey] = (friends[fromKey] || []).filter(f => f.username.toLowerCase() !== myKey);
    writeData('friends', friends);

    pushEvent(fromUsername, { type: 'friend_declined', by: username });
  }
  res.json({ success: true });
});

// ── Challenge Endpoints ─────────────────────────────────────
app.post('/api/challenge/send', requireAuth, (req, res) => {
  const { username } = req.user;
  const { targetUsername, color = 'random' } = req.body;
  if (!targetUsername) return res.status(400).json({ error: 'Target username is required' });

  if (getOnlineStatus(targetUsername.toLowerCase()) !== 'online') {
    return res.status(409).json({ error: 'User is not online or currently in game' });
  }

  const challengeId = uuidv4();
  const data = readData('games');
  data.pending = data.pending || {};
  data.pending[challengeId] = {
    id: challengeId,
    from: username,
    to: targetUsername,
    color,
    createdAt: Date.now()
  };
  writeData('games', data);

  // Challenge expires in 60s
  setTimeout(() => {
    const g = readData('games');
    if (g.pending && g.pending[challengeId]) {
      delete g.pending[challengeId];
      writeData('games', g);
      pushEvent(username, { type: 'challenge_expired', challengeId });
      pushEvent(targetUsername, { type: 'challenge_expired', challengeId });
    }
  }, 60000);

  pushEvent(targetUsername, { type: 'challenge_incoming', challengeId, from: username, color });
  res.json({ success: true, challengeId });
});

app.post('/api/challenge/respond', requireAuth, (req, res) => {
  const { username } = req.user;
  const { challengeId, accept } = req.body;
  if (!challengeId) return res.status(400).json({ error: 'Challenge ID is required' });

  const data = readData('games');
  const challenge = (data.pending || {})[challengeId];
  if (!challenge) return res.status(404).json({ error: 'Challenge not found or expired' });
  if (challenge.to.toLowerCase() !== username.toLowerCase()) return res.status(403).json({ error: 'Challenge was not sent to you' });

  delete data.pending[challengeId];

  if (!accept) {
    writeData('games', data);
    pushEvent(challenge.from, { type: 'challenge_declined', by: username });
    return res.json({ success: true });
  }

  // Determine colors
  let whitePlayer, blackPlayer;
  if (challenge.color === 'white') {
    whitePlayer = challenge.from;
    blackPlayer = challenge.to;
  } else if (challenge.color === 'black') {
    whitePlayer = challenge.to;
    blackPlayer = challenge.from;
  } else {
    if (Math.random() > 0.5) {
      whitePlayer = challenge.from;
      blackPlayer = challenge.to;
    } else {
      whitePlayer = challenge.to;
      blackPlayer = challenge.from;
    }
  }

  const gameId = uuidv4();
  data.active = data.active || {};
  data.active[gameId] = {
    id: gameId,
    white: whitePlayer,
    black: blackPlayer,
    moves: [],
    fen: 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1',
    status: 'active',
    winner: null,
    drawOffer: null,
    lastMoveAt: Date.now(),
    createdAt: Date.now()
  };
  writeData('games', data);

  const startEvent = { type: 'game_start', gameId, white: whitePlayer, black: blackPlayer };
  pushEvent(challenge.from, startEvent);
  pushEvent(challenge.to, startEvent);

  setInGame(whitePlayer.toLowerCase(), gameId);
  setInGame(blackPlayer.toLowerCase(), gameId);

  scheduleAbandonCheck(gameId);

  res.json({ success: true, gameId, white: whitePlayer, black: blackPlayer });
});

// ── Game Endpoints ──────────────────────────────────────────
app.post('/api/game/move', requireAuth, (req, res) => {
  const { username } = req.user;
  const { gameId, from, to, promotion } = req.body;
  if (!gameId || !from || !to) return res.status(400).json({ error: 'gameId, from, and to are required' });

  const data = readData('games');
  const game = (data.active || {})[gameId];
  if (!game) return res.status(404).json({ error: 'Game not found' });
  if (game.white.toLowerCase() !== username.toLowerCase() && game.black.toLowerCase() !== username.toLowerCase()) {
    return res.status(403).json({ error: 'Not authorized to play in this game' });
  }
  if (game.status !== 'active') return res.status(409).json({ error: 'Game has already ended' });

  // Turn check
  const moveCount = game.moves.length;
  const isWhiteTurn = moveCount % 2 === 0;
  if (isWhiteTurn && game.white.toLowerCase() !== username.toLowerCase()) return res.status(409).json({ error: 'Not your turn' });
  if (!isWhiteTurn && game.black.toLowerCase() !== username.toLowerCase()) return res.status(409).json({ error: 'Not your turn' });

  // Move validation
  const validation = validateMove(game.fen, from, to, promotion);
  if (!validation.valid) return res.status(400).json({ error: validation.reason });

  // Record move
  const moveRecord = {
    from,
    to,
    promotion: promotion || null,
    fen: validation.newFen,
    san: validation.san,
    timestamp: Date.now()
  };
  game.moves.push(moveRecord);
  game.fen = validation.newFen;
  game.lastMoveAt = Date.now();
  game.drawOffer = null; // Clear draw offer on move

  // Handle endings
  if (validation.isCheckmate) {
    game.status = 'checkmate';
    game.winner = username;
    clearInGame(game.white.toLowerCase());
    clearInGame(game.black.toLowerCase());
  } else if (validation.isStalemate || validation.isDraw) {
    game.status = validation.isStalemate ? 'stalemate' : 'draw';
    clearInGame(game.white.toLowerCase());
    clearInGame(game.black.toLowerCase());
  }

  writeData('games', data);

  const opponent = game.white.toLowerCase() === username.toLowerCase() ? game.black : game.white;
  const moveEvent = {
    type: 'game_move',
    gameId,
    from,
    to,
    promotion: promotion || null,
    fen: validation.newFen,
    san: validation.san,
    status: game.status,
    winner: game.winner
  };
  pushEvent(opponent, moveEvent);

  res.json({ success: true, fen: validation.newFen, status: game.status });
});

app.get('/api/game/state/:gameId', requireAuth, (req, res) => {
  const { username } = req.user;
  const { gameId } = req.params;
  const data = readData('games');
  const game = (data.active || {})[gameId];
  if (!game) return res.status(404).json({ error: 'Game not found' });

  const myKey = username.toLowerCase();
  if (game.white.toLowerCase() !== myKey && game.black.toLowerCase() !== myKey) {
    return res.status(403).json({ error: 'Not your game' });
  }

  res.json({
    gameId: game.id,
    white: game.white,
    black: game.black,
    fen: game.fen,
    moves: game.moves,
    status: game.status,
    winner: game.winner,
    yourColor: game.white.toLowerCase() === myKey ? 'white' : 'black'
  });
});

app.post('/api/game/resign', requireAuth, (req, res) => {
  const { username } = req.user;
  const { gameId } = req.body;
  const data = readData('games');
  const game = (data.active || {})[gameId];
  if (!game) return res.status(404).json({ error: 'Game not found' });
  if (game.white.toLowerCase() !== username.toLowerCase() && game.black.toLowerCase() !== username.toLowerCase()) {
    return res.status(403).json({ error: 'Not your game' });
  }
  if (game.status !== 'active') return res.status(409).json({ error: 'Game already ended' });

  const opponent = game.white.toLowerCase() === username.toLowerCase() ? game.black : game.white;
  game.status = 'resigned';
  game.winner = opponent;
  clearInGame(game.white.toLowerCase());
  clearInGame(game.black.toLowerCase());
  writeData('games', data);

  const endEvent = { type: 'game_ended', gameId, status: 'resigned', winner: opponent, loser: username };
  pushEvent(opponent, endEvent);
  res.json({ success: true });
});

app.post('/api/game/draw/offer', requireAuth, (req, res) => {
  const { username } = req.user;
  const { gameId } = req.body;
  const data = readData('games');
  const game = (data.active || {})[gameId];
  if (!game) return res.status(404).json({ error: 'Game not found' });
  if (game.white.toLowerCase() !== username.toLowerCase() && game.black.toLowerCase() !== username.toLowerCase()) {
    return res.status(403).json({ error: 'Not your game' });
  }
  if (game.status !== 'active') return res.status(409).json({ error: 'Game already ended' });

  game.drawOffer = username;
  writeData('games', data);

  const opponent = game.white.toLowerCase() === username.toLowerCase() ? game.black : game.white;
  pushEvent(opponent, { type: 'draw_offer', gameId, from: username });
  res.json({ success: true });
});

app.post('/api/game/draw/respond', requireAuth, (req, res) => {
  const { username } = req.user;
  const { gameId, accept } = req.body;
  const data = readData('games');
  const game = (data.active || {})[gameId];
  if (!game) return res.status(404).json({ error: 'Game not found' });
  if (game.white.toLowerCase() !== username.toLowerCase() && game.black.toLowerCase() !== username.toLowerCase()) {
    return res.status(403).json({ error: 'Not your game' });
  }
  if (game.status !== 'active') return res.status(409).json({ error: 'Game already ended' });
  if (!game.drawOffer) return res.status(400).json({ error: 'No active draw offer' });
  if (game.drawOffer.toLowerCase() === username.toLowerCase()) return res.status(400).json({ error: 'Cannot respond to your own draw offer' });

  const opponent = game.drawOffer;
  game.drawOffer = null;

  if (accept) {
    game.status = 'draw';
    clearInGame(game.white.toLowerCase());
    clearInGame(game.black.toLowerCase());
    writeData('games', data);

    const endEvent = { type: 'game_ended', gameId, status: 'draw', winner: null };
    pushEvent(game.white, endEvent);
    pushEvent(game.black, endEvent);
  } else {
    writeData('games', data);
    pushEvent(opponent, { type: 'draw_declined', gameId });
  }
  res.json({ success: true });
});

// ── SSE Events Endpoint ──────────────────────────────────────
app.get('/api/events', requireAuth, (req, res) => {
  const { username } = req.user;
  const key = username.toLowerCase();

  closeSseForUser(key);

  res.setHeader('Content-Type', 'text/event-stream');
  res.setHeader('Cache-Control', 'no-cache');
  res.setHeader('Connection', 'keep-alive');
  res.setHeader('X-Accel-Buffering', 'no');
  res.flushHeaders();

  sendSseEvent(res, { type: 'connected', username });

  // Heartbeat every 20s
  const heartbeatInterval = setInterval(() => {
    try {
      res.write('data: {"type":"ping"}\n\n');
    } catch (e) {
      closeSseForUser(key);
    }
  }, 20000);

  sseConnections.set(key, { res, heartbeatInterval });

  // Notify friends that this user is online
  broadcastToFriends(username, { type: 'friend_status', username, status: 'online' });

  // Stream backlog offline notifications
  const q = offlineQueues.get(key) || [];
  q.forEach(event => sendSseEvent(res, event));
  offlineQueues.delete(key);

  req.on('close', () => {
    closeSseForUser(key);
    broadcastToFriends(username, { type: 'friend_status', username, status: 'offline' });
  });
});

// ── Static Web App serving ────────────────────────────────────
app.get('/', (req, res) => {
  res.sendFile(path.join(PUBLIC_DIR, 'chess', 'index.html'));
});
app.get('/chess', (req, res) => {
  res.sendFile(path.join(PUBLIC_DIR, 'chess', 'index.html'));
});
app.get('/chess/', (req, res) => {
  res.sendFile(path.join(PUBLIC_DIR, 'chess', 'index.html'));
});
app.use(express.static(PUBLIC_DIR));


// ══════════════════════════════════════════════════════════════════
// QUICK ROOM API (no login needed — 6-char code based P2P relay)
// ══════════════════════════════════════════════════════════════════
const quickRooms = new Map(); // code -> { hostSse, guestSse, chess, moves, status, created }

function genRoomCode() {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'; // no ambiguous I,O,0,1
  let code = '';
  for (let i = 0; i < 6; i++) code += chars[Math.floor(Math.random() * chars.length)];
  return code;
}

function roomSend(room, role, data) {
  const sse = role === 'host' ? room.hostSse : room.guestSse;
  if (sse && !sse.writableEnded) {
    try { sse.write(`data: ${JSON.stringify(data)}\n\n`); } catch(e) {}
  }
}

function roomBroadcast(room, data) {
  roomSend(room, 'host', data);
  roomSend(room, 'guest', data);
}

// Clean up rooms older than 2 hours
setInterval(() => {
  const now = Date.now();
  for (const [code, room] of quickRooms) {
    if (now - room.created > 2 * 60 * 60 * 1000) {
      quickRooms.delete(code);
    }
  }
}, 5 * 60 * 1000);

// POST /api/room/create — host creates room
app.post('/api/room/create', (req, res) => {
  let code;
  do { code = genRoomCode(); } while (quickRooms.has(code));
  quickRooms.set(code, {
    hostSse: null, guestSse: null,
    chess: null, moves: [], status: 'waiting',
    created: Date.now(), hostColor: 'w'
  });
  res.json({ ok: true, code });
});

// GET /api/room/events/:code?role=host|guest — SSE stream
app.get('/api/room/events/:code', (req, res) => {
  const { code } = req.params;
  const role = req.query.role;
  const room = quickRooms.get(code);
  if (!room) return res.status(404).json({ error: 'Room not found' });

  res.setHeader('Content-Type', 'text/event-stream');
  res.setHeader('Cache-Control', 'no-cache');
  res.setHeader('Connection', 'keep-alive');
  res.flushHeaders();

  if (role === 'host') {
    room.hostSse = res;
    // Notify host they are connected
    res.write(`data: ${JSON.stringify({ type: 'host_ready', code })}\n\n`);
  } else {
    room.guestSse = res;
    room.status = 'playing';
    // Start game — host=White, guest=Black
    let ChessGame;
    try { ChessGame = require('chess.js').Chess; } catch(e) { ChessGame = require('chess.js'); }
    room.chess = new ChessGame();
    room.moves = [];
    // Notify both players
    roomSend(room, 'host',  { type: 'game_start', yourColor: 'w', opponentColor: 'b', fen: room.chess.fen() });
    roomSend(room, 'guest', { type: 'game_start', yourColor: 'b', opponentColor: 'w', fen: room.chess.fen() });
  }

  const heartbeat = setInterval(() => {
    try { res.write(': heartbeat\n\n'); } catch(e) { clearInterval(heartbeat); }
  }, 25000);

  req.on('close', () => {
    clearInterval(heartbeat);
    if (role === 'host') room.hostSse = null;
    else {
      room.guestSse = null;
      // Notify host guest left
      roomSend(room, 'host', { type: 'opponent_left' });
    }
  });
});

// POST /api/room/move — relay a move
app.post('/api/room/move', (req, res) => {
  const { code, from, to, promotion, role } = req.body;
  const room = quickRooms.get(code);
  if (!room) return res.status(404).json({ error: 'Room not found' });
  if (!room.chess) return res.status(400).json({ error: 'Game not started' });

  const expectedTurn = room.chess.turn() === 'w' ? 'host' : 'guest';
  if (role !== expectedTurn) return res.status(400).json({ error: 'Not your turn' });

  let moveResult;
  try {
    moveResult = room.chess.move({ from, to, promotion: promotion || undefined });
  } catch(e) { moveResult = null; }

  if (!moveResult) return res.status(400).json({ error: 'Illegal move' });

  room.moves.push({ from, to, promotion, san: moveResult.san });
  const fen = room.chess.fen();
  const opponent = role === 'host' ? 'guest' : 'host';
  roomSend(room, opponent, { type: 'move', from, to, promotion, san: moveResult.san, fen });

  let gameOver = null;
  if (room.chess.in_checkmate()) gameOver = { reason: 'checkmate', winner: role };
  else if (room.chess.in_stalemate()) gameOver = { reason: 'stalemate', winner: null };
  else if (room.chess.in_draw()) gameOver = { reason: 'draw', winner: null };

  if (gameOver) {
    roomBroadcast(room, { type: 'game_over', ...gameOver, fen });
    room.status = 'finished';
  }

  res.json({ ok: true, san: moveResult.san, fen, gameOver });
});

// POST /api/room/resign
app.post('/api/room/resign', (req, res) => {
  const { code, role } = req.body;
  const room = quickRooms.get(code);
  if (!room) return res.status(404).json({ error: 'Room not found' });
  const opponent = role === 'host' ? 'guest' : 'host';
  roomSend(room, opponent, { type: 'game_over', reason: 'resigned', winner: opponent });
  room.status = 'finished';
  res.json({ ok: true });
});

// POST /api/room/rematch
app.post('/api/room/rematch', (req, res) => {
  const { code } = req.body;
  const room = quickRooms.get(code);
  if (!room) return res.status(404).json({ error: 'Room not found' });
  let ChessGame;
  try { ChessGame = require('chess.js').Chess; } catch(e) { ChessGame = require('chess.js'); }
  room.chess = new ChessGame();
  room.moves = [];
  room.status = 'playing';
  // Swap colors for rematch
  room.hostColor = room.hostColor === 'w' ? 'b' : 'w';
  const hc = room.hostColor, gc = hc === 'w' ? 'b' : 'w';
  roomSend(room, 'host',  { type: 'game_start', yourColor: hc, opponentColor: gc, fen: room.chess.fen() });
  roomSend(room, 'guest', { type: 'game_start', yourColor: gc, opponentColor: hc, fen: room.chess.fen() });
  res.json({ ok: true });
});

// GET /api/room/check/:code — check if room exists
app.get('/api/room/check/:code', (req, res) => {
  const room = quickRooms.get(req.params.code.toUpperCase());
  res.json({ exists: !!room, status: room?.status || null });
});

// ── Fallback 404 ──────────────────────────────────────────────
app.use((req, res) => {
  res.status(404).send('404 Not Found');
});

app.listen(PORT, '0.0.0.0', () => {
  console.log(`ChessOmania running at http://localhost:${PORT}/`);
  console.log(`Network access: http://0.0.0.0:${PORT}/`);
  console.log(`Serving static files from ${PUBLIC_DIR}`);

  // Show local network IPs for easy access
  const os = require('os');
  const networkInterfaces = os.networkInterfaces();
  console.log('\nAccess from other devices on network:');
  Object.keys(networkInterfaces).forEach(interfaceName => {
    networkInterfaces[interfaceName].forEach(iface => {
      if (iface.family === 'IPv4' && !iface.internal) {
        console.log(`  http://${iface.address}:${PORT}/`);
      }
    });
  });
});
