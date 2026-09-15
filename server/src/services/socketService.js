const { WebSocketServer, WebSocket } = require('ws');
const { Server: SocketIOServer } = require('socket.io');
const { createClient } = require('redis');
const { createAdapter } = require('@socket.io/redis-adapter');

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
  // 1. Raw WebSocket Server for Android OkHttp WebSocket
  wss = new WebSocketServer({ server, path: '/ws' });

  wss.on('connection', (ws, req) => {
    clientSubscriptions.set(ws, new Set());
    ws.isAlive = true;

    ws.on('pong', () => {
      ws.isAlive = true;
    });

    ws.on('message', (message) => {
      try {
        const data = JSON.parse(message.toString());
        const { action, channel } = data;

        if (action === 'subscribe' && channel) {
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

    // Welcome acknowledgment
    ws.send(JSON.stringify({
      type: 'CONNECTED',
      message: 'Connected to Quick Bite Real-Time WebSocket Gateway',
      timestamp: Date.now(),
    }));
  });

  // Heartbeat interval to prune dead sockets
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

  // 2. Socket.IO Gateway
  try {
    io = new SocketIOServer(server, {
      cors: { origin: '*', methods: ['GET', 'POST', 'PATCH'] },
      path: '/socket.io',
      transports: ['websocket', 'polling'],
    });

    io.on('connection', (socket) => {
      socket.on('join', (channel) => {
        if (channel) socket.join(channel);
      });
      socket.on('leave', (channel) => {
        if (channel) socket.leave(channel);
      });
      socket.on('subscribe', (channel) => {
        if (channel) socket.join(channel);
      });
      socket.on('unsubscribe', (channel) => {
        if (channel) socket.leave(channel);
      });
    });
    console.log('⚡ Quick Bite Socket.IO Gateway initialized on /socket.io');
  } catch (err) {
    console.warn('[SocketService] Socket.IO initialization warning:', err.message);
  }

  // 3. Multi-Instance Redis Adapter Setup (Render Multi-Instance Horizontal Scaling)
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
  // 1. Dispatch to local raw WebSocket clients
  dispatchLocalRawWs(channel, payload);

  // 2. Publish to Redis so other Render instances dispatch to their local WebSocket clients
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

  // 3. Broadcast via Socket.IO (automatically synced across instances by Socket.IO Redis Adapter)
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
  const payload = {
    type: 'ORDER_STATUS_UPDATED',
    order,
    timestamp: Date.now(),
  };

  // Broadcast to order channel
  if (order.orderId) {
    broadcastToChannel(`order:${order.orderId}`, payload);
  }
  // Broadcast to student channel
  if (order.studentId) {
    broadcastToChannel(`student:${order.studentId}`, payload);
  }
  // Broadcast to canteen owner channel
  if (order.canteenId) {
    broadcastToChannel(`canteen:${order.canteenId}`, payload);
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
