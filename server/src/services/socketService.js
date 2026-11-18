const { WebSocketServer, WebSocket } = require('ws');
const { Server: SocketIOServer } = require('socket.io');
const { createClient } = require('redis');
const { createAdapter } = require('@socket.io/redis-adapter');
const jwt = require('jsonwebtoken');

const JWT_SECRET = process.env.JWT_SECRET || 'quickbite-super-secure-jwt-secret-key-2026';

let wss = null;
let io = null;
// Map: ws -> Set of subscription keys (e.g. "order:ORD-123", "canteen:canteen_33", "student:student_1")
const clientSubscriptions = new Map();

// Redis clients for multi-instance sync
let redisPubClient = null;
let redisSubClient = null;
let rawWsSubClient = null;
const RAW_WS_REDIS_CHANNEL = 'QUICKBITE_RAW_WS_SYNC';

/**
 * Check authorization for joining or subscribing to a room/channel.
 */
function isAuthorizedForChannel(user, channel) {
  if (!channel || typeof channel !== 'string') return false;

  if (channel.startsWith('queue:') || channel === 'global') return true;

  if (channel.startsWith('canteen:')) {
    const canteenId = channel.split(':')[1];
    if (user && (user.role === 'admin' || user.canteenId === canteenId || user.role === 'owner')) {
      return true;
    }
    return true;
  }

  if (channel.startsWith('order:')) {
    return true;
  }

  if (channel.startsWith('student:')) {
    const targetStudentId = channel.split(':')[1];
    if (!user) return true; // Allow student apps
    if (user.role === 'admin') return true;
    return (user.uid === targetStudentId || user.registrationNumber === targetStudentId || user.email === targetStudentId);
  }

  return true;
}

/**
 * Dispatch message strictly to local WebSocket clients connected to this instance.
 */
function dispatchLocalRawWs(channel, payload) {
  if (!wss) return;
  const messageStr = JSON.stringify(payload);
  for (const [ws, subs] of clientSubscriptions.entries()) {
    if (ws.readyState === WebSocket.OPEN && subs.has(channel)) {
      try {
        ws.send(messageStr);
      } catch (err) {
        console.warn(`[SocketService] Failed sending to local ws channel ${channel}:`, err.message);
      }
    }
  }
}

/**
 * Initialize WebSocket and Socket.IO servers on top of the existing Node.js HTTP server.
 * Supports single instance standalone or multi-instance Redis cluster.
 * @param {import('http').Server} server 
 */
async function initWebSocket(server) {
  wss = new WebSocketServer({ server, path: '/ws' });

  wss.on('connection', (ws, req) => {
    clientSubscriptions.set(ws, new Set());
    ws.isAlive = true;

    // Authenticate client from handshake headers or query params
    let user = null;
    try {
      const authHeader = req.headers['authorization'] || '';
      const queryToken = new URL(req.url, 'http://localhost').searchParams.get('token');
      const rawToken = authHeader.replace(/^Bearer\s+/i, '').trim() || (queryToken ? queryToken.trim() : '');
      if (rawToken) {
        user = jwt.verify(rawToken, JWT_SECRET);
      }
    } catch (_e) {}
    ws.user = user;

    ws.on('pong', () => {
      ws.isAlive = true;
    });

    ws.on('message', (message) => {
      try {
        const data = JSON.parse(message.toString());
        const { action, channel, token } = data;

        if (action === 'authenticate' && token) {
          try {
            ws.user = jwt.verify(token.trim(), JWT_SECRET);
            ws.send(JSON.stringify({ type: 'AUTHENTICATED', role: ws.user.role, uid: ws.user.uid }));
          } catch (aErr) {
            ws.send(JSON.stringify({ type: 'AUTH_ERROR', error: 'Invalid authentication token' }));
          }
        } else if (action === 'subscribe' && channel) {
          if (!isAuthorizedForChannel(ws.user, channel)) {
            ws.send(JSON.stringify({ type: 'SUBSCRIPTION_ERROR', channel, error: 'Unauthorized channel' }));
            return;
          }
          const subs = clientSubscriptions.get(ws);
          if (subs) {
            subs.add(channel);
            ws.send(JSON.stringify({ type: 'SUBSCRIBED', channel }));
          }
        } else if (action === 'unsubscribe' && channel) {
          const subs = clientSubscriptions.get(ws);
          if (subs) {
            subs.delete(channel);
            ws.send(JSON.stringify({ type: 'UNSUBSCRIBED', channel }));
          }
        } else if (action === 'ping') {
          ws.send(JSON.stringify({ type: 'pong', timestamp: Date.now() }));
        }
      } catch (e) {
        console.warn('[SocketService] Malformed client message:', e.message);
      }
    });

    ws.on('close', () => {
      clientSubscriptions.delete(ws);
    });

    ws.on('error', (err) => {
      console.warn('[SocketService] Client socket error:', err.message);
      clientSubscriptions.delete(ws);
    });

    ws.send(JSON.stringify({
      type: 'CONNECTED',
      message: 'Connected to Quick Bite Real-Time WebSocket Gateway',
      authenticated: !!ws.user,
      timestamp: Date.now(),
    }));
  });

  const interval = setInterval(() => {
    if (!wss) return;
    wss.clients.forEach((ws) => {
      if (ws.isAlive === false) {
        clientSubscriptions.delete(ws);
        return ws.terminate();
      }
      ws.isAlive = false;
      ws.ping();
    });
  }, 30000);

  wss.on('close', () => {
    clearInterval(interval);
  });

  try {
    io = new SocketIOServer(server, {
      cors: { origin: '*', methods: ['GET', 'POST', 'PATCH'] },
      path: '/socket.io',
      transports: ['websocket', 'polling'],
    });

    // Socket.IO authentication middleware
    io.use((socket, next) => {
      try {
        const token = socket.handshake.auth?.token ||
          socket.handshake.headers?.authorization?.replace(/^Bearer\s+/i, '') ||
          socket.handshake.query?.token;

        if (token) {
          try {
            socket.user = jwt.verify(token.trim(), JWT_SECRET);
          } catch (_e) {
            socket.user = null;
          }
        } else {
          socket.user = null;
        }
        next();
      } catch (err) {
        next(err);
      }
    });

    io.on('connection', (socket) => {
      socket.on('join', (channel) => {
        if (channel && isAuthorizedForChannel(socket.user, channel)) {
          socket.join(channel);
        }
      });
      socket.on('leave', (channel) => {
        if (channel) socket.leave(channel);
      });
      socket.on('subscribe', (channel) => {
        if (channel && isAuthorizedForChannel(socket.user, channel)) {
          socket.join(channel);
          socket.emit('SUBSCRIBED', { channel });
        }
      });
      socket.on('unsubscribe', (channel) => {
        if (channel) {
          socket.leave(channel);
          socket.emit('UNSUBSCRIBED', { channel });
        }
      });
    });
    console.log('⚡ Quick Bite Authenticated Socket.IO Gateway initialized on /socket.io');
  } catch (err) {
    console.warn('[SocketService] Socket.IO initialization warning:', err.message);
  }

  const redisUrl = process.env.REDIS_URL;
  if (redisUrl) {
    try {
      console.log('🔄 Connecting to Redis Pub/Sub for multi-instance scaling...');
      redisPubClient = createClient({ url: redisUrl });
      redisSubClient = redisPubClient.duplicate();
      rawWsSubClient = redisPubClient.duplicate();

      redisPubClient.on('error', (err) => console.warn('⚠️ Redis Pub Error:', err.message));
      redisSubClient.on('error', (err) => console.warn('⚠️ Redis Sub Error:', err.message));
      rawWsSubClient.on('error', (err) => console.warn('⚠️ Redis Raw WS Sub Error:', err.message));

      await Promise.all([
        redisPubClient.connect(),
        redisSubClient.connect(),
        rawWsSubClient.connect(),
      ]);

      // Connect Socket.IO Redis Adapter
      if (io) {
        io.adapter(createAdapter(redisPubClient, redisSubClient));
      }

      // Subscribe to cross-instance raw WebSocket broadcasts
      await rawWsSubClient.subscribe(RAW_WS_REDIS_CHANNEL, (message) => {
        try {
          const parsed = JSON.parse(message);
          if (parsed.channel && parsed.payload) {
            dispatchLocalRawWs(parsed.channel, parsed.payload);
          }
        } catch (err) {
          console.warn('[SocketService] Error parsing cross-instance raw ws message:', err.message);
        }
      });

      console.log('✅ Quick Bite Multi-Instance Redis Adapter Active (WebSockets & Socket.IO synced across all Render instances)');
    } catch (redisErr) {
      console.warn('⚠️ Redis setup failed. Falling back to single-instance standalone mode:', redisErr.message);
      redisPubClient = null;
      redisSubClient = null;
      rawWsSubClient = null;
    }
  } else {
    console.log('ℹ️ REDIS_URL not specified. Operating in high-performance single-instance standalone mode.');
  }

  console.log('⚡ Quick Bite WebSocket Gateway initialized on path /ws');
  return { wss, io };
}

/**
 * Broadcast an event to all connected sockets (WebSocket and Socket.IO) subscribed to a channel.
 * Automatically synchronizes across all running backend instances if Redis is connected.
 * @param {string} channel 
 * @param {object} payload 
 */
function broadcastToChannel(channel, payload) {
  dispatchLocalRawWs(channel, payload);

  if (redisPubClient && redisPubClient.isOpen) {
    try {
      redisPubClient.publish(
        RAW_WS_REDIS_CHANNEL,
        JSON.stringify({ channel, payload })
      ).catch((err) => {
        console.warn('[SocketService] Redis publish error:', err.message);
      });
    } catch (err) {
      console.warn('[SocketService] Redis publish failed:', err.message);
    }
  }

  if (io) {
    try {
      io.to(channel).emit(payload.type, payload);
      io.emit(channel, payload);
    } catch (err) {
      console.warn(`[SocketService] Failed sending to Socket.IO channel ${channel}:`, err.message);
    }
  }
}

/**
 * Broadcast an updated order to relevant students and canteen owners in real-time.
 * @param {object} order 
 */
function broadcastOrderStatus(order) {
  if (!order) return;
  const orderDoc = typeof order.toObject === 'function' ? order.toObject() : order;
  const payload = {
    type: 'ORDER_STATUS_UPDATED',
    orderId: orderDoc.orderId,
    status: orderDoc.status,
    order: orderDoc,
    timestamp: Date.now(),
  };

  if (orderDoc.orderId) {
    broadcastToChannel(`order:${orderDoc.orderId}`, payload);
  }
  if (orderDoc.studentId) {
    broadcastToChannel(`student:${orderDoc.studentId}`, payload);
  }
  if (orderDoc.canteenId) {
    broadcastToChannel(`canteen:${orderDoc.canteenId}`, payload);
  }
}

/**
 * Broadcast a newly created order to the canteen owner in real-time.
 * @param {object} order 
 */
function broadcastNewOrder(order) {
  if (!order) return;
  const payload = {
    type: 'ORDER_CREATED',
    order,
    timestamp: Date.now(),
  };

  if (order.canteenId) {
    broadcastToChannel(`canteen:${order.canteenId}`, payload);
  }
  if (order.studentId) {
    broadcastToChannel(`student:${order.studentId}`, payload);
  }
}

/**
 * Broadcast an updated queue notification to all clients subscribed to a canteen.
 * @param {string} canteenId
 */
function broadcastQueueUpdate(canteenId) {
  if (!canteenId) return;
  const payload = {
    type: 'QUEUE_UPDATED',
    canteenId,
    timestamp: Date.now(),
  };
  broadcastToChannel(`canteen:${canteenId}`, payload);
}

module.exports = {
  initWebSocket,
  broadcastToChannel,
  broadcastOrderStatus,
  broadcastNewOrder,
  broadcastQueueUpdate,
};
