const { WebSocketServer, WebSocket } = require('ws');

let wss = null;
// Map: ws -> Set of subscription keys (e.g. "order:ORD-123", "canteen:canteen_33", "student:student_1")
const clientSubscriptions = new Map();

/**
 * Initialize the WebSocket Server on top of the existing Node.js HTTP server.
 * @param {import('http').Server} server 
 */
function initWebSocket(server) {
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

  console.log('⚡ Quick Bite WebSocket Gateway initialized on path /ws');
  return wss;
}

/**
 * Broadcast an event to all connected sockets subscribed to a specific channel.
 * @param {string} channel 
 * @param {object} payload 
 */
function broadcastToChannel(channel, payload) {
  if (!wss) return;
  const messageStr = JSON.stringify(payload);

  for (const [ws, subs] of clientSubscriptions.entries()) {
    if (ws.readyState === WebSocket.OPEN && subs.has(channel)) {
      try {
        ws.send(messageStr);
      } catch (err) {
        console.warn(`[SocketService] Failed sending to channel ${channel}:`, err.message);
      }
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

module.exports = {
  initWebSocket,
  broadcastToChannel,
  broadcastOrderStatus,
  broadcastNewOrder,
};
