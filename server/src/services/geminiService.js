/**
 * Gemini Service for QuickBite – MunchMate
 * Implements Google Gemini function calling / tool calling, ultra-fast streaming,
 * multi-turn dialogue, authoritative grounding against MongoDB records,
 * and context-aware fallback execution.
 */

const crypto = require('crypto');
const { TOOL_DECLARATIONS, executeTool, getStatusExplanation } = require('./aiToolsService');

const GEMINI_API_BASE = 'https://generativelanguage.googleapis.com/v1beta/models';

// Preferred production models verified with Google Gemini API
const DEFAULT_MODEL = process.env.GEMINI_MODEL || 'gemini-2.0-flash';
const FALLBACK_MODELS = [
  'gemini-2.0-flash',
  'gemini-1.5-flash',
  'gemini-1.5-pro',
];
const REQUEST_TIMEOUT_MS = 10000;

const SYSTEM_INSTRUCTION = `You are MunchMate, the official AI food-ordering assistant for the QuickBite campus canteen app ("MunchMate by QuickBite").
Your purpose is to assist students with live food recommendations, canteen menus, accurate order tracking, order history, cart information, dietary suggestions, and QuickBite campus FAQs.

CAMPUS KNOWLEDGE BASE & FAQs:
• Official Support Email: quickbite.connecting@gmail.com
• Canteen Operating Hours: Monday to Saturday 7:30 AM – 9:30 PM | Sunday 8:00 AM – 8:00 PM
• Campus Canteens:
  - Govinda's Kitchen: Block 33 (Ground Floor, North Wing) — South Indian classics, Dosas, Thalis, Filter Coffee.
  - Talk of the Town: Block 34 (Food Court, 1st Floor) — Burgers, Pizzas, Sandwiches, Cold Coffee, Juices.
• Token & Pickup Process: Every placed order receives a digital Token Number (e.g. #042). When the order status turns "Ready for Pickup", go to the canteen counter and present your token number to pick up your freshly prepared meal.
• Order Cancellation Policy: Orders can be cancelled while in "Placed" or "Waiting/Accepted" status before kitchen preparation begins. Once the canteen starts "Preparing", cancellation is locked to avoid food wastage.
• "Your Usual" Feature: Accessible right from the Home screen, "Your Usual" enables instant 1-tap reordering of your most frequently ordered meals with your pre-selected options.
• Dietary & Budget Queries: Understand and answer queries for spicy food, sweet treats, healthy snacks, breakfast items, and budget meals under ₹50, ₹100, etc.

STRICT OPERATIONAL RULES:
1. Identity: You are MunchMate by QuickBite. Never refer to yourself as Gemini, Google AI, or any other LLM.
2. 100% Data Accuracy & Grounding:
   - When answering questions about available food, menu items, prices, canteen details, order tracking, order history, cart, or reviews, ALWAYS call the available tools (search_food_item, get_order_status_summary, get_my_orders, get_order_tracking, get_menu_items, get_canteen_details, get_cart_details, get_favourite_items, get_recent_orders, manage_cart, get_food_reviews).
   - Base all answers STRICTLY on tool results and verified application data.
   - NEVER invent or hallucinate food items, prices, order statuses, tokens, ratings, or reviews.
3. Specific Dish Queries (Availability & Pricing):
   - When asked "Are pancakes available?", "Do you have dosa?", "What is the price of burger?", or any specific dish query:
     * Call search_food_item with the dish name.
     * If the item is NOT found on campus menus (found: false), state clearly that the dish is not currently offered on any campus canteen menu, and recommend real available dishes from the canteen. NEVER falsely claim an unlisted dish is available.
     * If found but available is false, state clearly that the dish is on the menu but currently SOLD OUT.
     * If found and available is true, state its verified price (₹), canteen name, and rating.
4. Smart Order Tracking Flow:
   - When asked "Where is my order?", "Is my order ready?", "Track my order", "Show my latest order", or about active orders:
     * Call get_order_status_summary to inspect the student's active orders.
     * If scenario is NO_ACTIVE_ORDERS: State clearly that the student currently has no active orders. Do NOT invent an order. Offer to explore today's menu.
     * If scenario is SINGLE_ACTIVE_ORDER: Provide full details for that order: Order ID, canteen, current status with explanation, token number, items, total amount, estimated ready time, and pickup location.
     * If scenario is MULTIPLE_ACTIVE_ORDERS: Present a brief summary of each order (e.g. Order 1: #ORD-123 at Govinda's Kitchen - Preparing; Order 2: #ORD-456 at Talk of the Town - Ready for Pickup). Explicitly ask the student which order they would like to track in detail.
     * When student specifies an order ID or asks follow-up questions ("what about the second one?", "is that ready now?", "how much did I pay for it?"): Use get_order_tracking to retrieve fresh authoritative tracking and timeline data for that specific order.
   - Order Timeline: When tracking an order, reference the actual timeline steps (Placed, Accepted, Preparing, Ready, Completed). Show timestamps only when recorded in the database. Do not fabricate times.
5. Favourites & Order History:
   - When asked "Show my favourite items" or about saved dishes, call get_favourite_items.
   - When asked "What did I order recently?" or about past orders, call get_recent_orders.
6. Cart Management:
   - When asked "What is in my cart?", call get_cart_details.
   - When asked to add, remove, or clear cart items ("Add 2 to my cart", "Clear my cart"), call manage_cart.
7. Support & FAQs:
   - If asked about canteen timings, operating hours, how to pick up food, order cancellation, refunds, or support contact, provide immediate accurate answers citing quickbite.connecting@gmail.com and operating timings.
8. No Repeated or Unnecessary Apologies & No Robot Intros:
   - Do NOT repeatedly introduce yourself with "I am MunchMate, your QuickBite assistant..." on normal questions. Only introduce yourself if the user explicitly asks who you are or greets you.
   - Do not apologize for normal situations (e.g. if a student has no active orders or cart is empty). Simply state the facts clearly, concisely, and supportively.
9. Personalised Greetings:
   - When greeting a student, greet them warmly by their first name and time of day.
   - For follow-up questions during ongoing conversations, do NOT repeat greetings. Answer directly and naturally.
10. Privacy & Security:
    - You only have access to the authenticated student's data. If an order belongs to someone else, state that for privacy reasons you can only view their own orders.
11. Concise & Friendly Tone:
    - Friendly, warm, concise, and helpful. Format prices clearly in Rupees (₹). Keep answers structured and readable.`;

/**
 * Sanitizes conversation history to guarantee strict alternating 'user' and 'model' turns
 * as required by the Gemini API specification.
 */
function sanitizeConversationHistory(history = []) {
  const cleanTurns = [];
  let expectedRole = 'user';

  for (const turn of history) {
    if (!turn || !turn.content || typeof turn.content !== 'string') continue;
    const text = turn.content.trim();
    if (!text) continue;

    const rawRole = (turn.role || '').toLowerCase();
    const role = (rawRole === 'user') ? 'user' : 'model';

    if (cleanTurns.length === 0) {
      if (role === 'user') {
        cleanTurns.push({ role, parts: [{ text }] });
        expectedRole = 'model';
      }
    } else {
      if (role === expectedRole) {
        cleanTurns.push({ role, parts: [{ text }] });
        expectedRole = (expectedRole === 'user') ? 'model' : 'user';
      } else if (cleanTurns.length > 0 && role !== expectedRole) {
        cleanTurns[cleanTurns.length - 1].parts[0].text += `\n${text}`;
      }
    }
  }

  if (cleanTurns.length > 0 && cleanTurns[cleanTurns.length - 1].role === 'user') {
    cleanTurns.pop();
  }

  return cleanTurns.slice(-4);
}

/**
 * Builds request payload with tool declarations and system instructions
 */
function buildPayload({ userMessage, verifiedContext, conversationHistory, includeTools = true }) {
  const cleanHistory = sanitizeConversationHistory(conversationHistory);

  const promptWithContext = `=== VERIFIED QUICKBITE APPLICATION DATA ===
${verifiedContext || 'No specific database records pre-matched.'}
===========================================

STUDENT MESSAGE:
${userMessage}`;

  const payload = {
    systemInstruction: {
      parts: [{ text: SYSTEM_INSTRUCTION }],
    },
    contents: [
      ...cleanHistory,
      {
        role: 'user',
        parts: [{ text: promptWithContext }],
      },
    ],
    generationConfig: {
      temperature: 0.2,
      topK: 30,
      topP: 0.9,
      maxOutputTokens: 600,
    },
  };

  if (includeTools) {
    payload.tools = [
      {
        functionDeclarations: TOOL_DECLARATIONS,
      },
    ];
  }

  return payload;
}

/**
 * Standard non-streaming request with Gemini Tool Calling execution loop.
 */
async function generateAiResponse({
  userMessage,
  verifiedContext,
  conversationHistory = [],
  authenticatedStudent = {},
  requestContext = {},
}) {
  const apiKey = process.env.GEMINI_API_KEY;

  if (!apiKey || apiKey.trim() === '' || apiKey === 'your-gemini-api-key') {
    const fallback = await generateFallbackResponseAsync(userMessage, verifiedContext, authenticatedStudent, requestContext);
    return {
      reply: fallback,
      modelUsed: 'deterministic-verified-context',
      toolCallsExecuted: [],
    };
  }

  const modelCandidateList = [
    process.env.GEMINI_MODEL || DEFAULT_MODEL,
    ...FALLBACK_MODELS,
  ].filter((m, i, arr) => arr.indexOf(m) === i);

  const requestPayload = buildPayload({ userMessage, verifiedContext, conversationHistory, includeTools: true });
  const toolCallsExecuted = [];

  for (const model of modelCandidateList) {
    const endpoint = `${GEMINI_API_BASE}/${encodeURIComponent(model)}:generateContent?key=${encodeURIComponent(apiKey)}`;

    try {
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);

      const response = await fetch(endpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(requestPayload),
        signal: controller.signal,
      });

      clearTimeout(timeoutId);

      if (!response.ok) {
        const errText = await response.text();
        console.warn(`[GeminiService] Model ${model} returned ${response.status}: ${errText.slice(0, 120)}`);
        continue;
      }

      const data = await response.json();
      const candidate = data.candidates?.[0];
      const parts = candidate?.content?.parts || [];

      const functionCallPart = parts.find(p => p.functionCall);

      if (functionCallPart && functionCallPart.functionCall) {
        const { name, args } = functionCallPart.functionCall;
        toolCallsExecuted.push({ name, args });

        const toolResult = await executeTool({
          toolName: name,
          args: args || {},
          authenticatedStudent,
          requestContext,
        });

        const secondPayload = {
          systemInstruction: requestPayload.systemInstruction,
          contents: [
            ...requestPayload.contents,
            {
              role: 'model',
              parts: [{ functionCall: { name, args: args || {} } }],
            },
            {
              role: 'function',
              parts: [
                {
                  functionResponse: {
                    name,
                    response: toolResult,
                  },
                },
              ],
            },
          ],
          generationConfig: requestPayload.generationConfig,
        };

        const secondResponse = await fetch(endpoint, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(secondPayload),
        });

        if (secondResponse.ok) {
          const secondData = await secondResponse.json();
          const finalReply = secondData.candidates?.[0]?.content?.parts?.[0]?.text;
          if (finalReply && finalReply.trim()) {
            return {
              reply: finalReply.trim(),
              modelUsed: model,
              toolCallsExecuted,
            };
          }
        }
      }

      const replyText = parts[0]?.text;
      if (replyText && replyText.trim()) {
        return {
          reply: replyText.trim(),
          modelUsed: model,
          toolCallsExecuted,
        };
      }
    } catch (e) {
      console.warn(`[GeminiService] Error with ${model}: ${e.message}`);
    }
  }

  // Graceful deterministic fallback
  const fallback = await generateFallbackResponseAsync(userMessage, verifiedContext, authenticatedStudent, requestContext);
  return {
    reply: fallback,
    modelUsed: 'deterministic-verified-context',
    toolCallsExecuted,
  };
}

/**
 * Streams AI response chunk-by-chunk over SSE with function calling support.
 */
async function streamAiResponse({
  userMessage,
  verifiedContext,
  conversationHistory = [],
  authenticatedStudent = {},
  requestContext = {},
  onChunk,
}) {
  const apiKey = process.env.GEMINI_API_KEY;

  if (!apiKey || apiKey.trim() === '' || apiKey === 'your-gemini-api-key') {
    const fallback = await generateFallbackResponseAsync(userMessage, verifiedContext, authenticatedStudent, requestContext);
    const words = fallback.split(' ');
    for (const w of words) {
      if (onChunk) onChunk(w + ' ');
      await new Promise(r => setTimeout(r, 15));
    }
    return { reply: fallback, modelUsed: 'deterministic-verified-context', toolCallsExecuted: [] };
  }

  const modelCandidateList = [
    process.env.GEMINI_MODEL || DEFAULT_MODEL,
    ...FALLBACK_MODELS,
  ].filter((m, i, arr) => arr.indexOf(m) === i);

  const requestPayload = buildPayload({ userMessage, verifiedContext, conversationHistory, includeTools: true });
  const toolCallsExecuted = [];

  for (const model of modelCandidateList) {
    const generateEndpoint = `${GEMINI_API_BASE}/${encodeURIComponent(model)}:generateContent?key=${encodeURIComponent(apiKey)}`;
    const streamEndpoint = `${GEMINI_API_BASE}/${encodeURIComponent(model)}:streamGenerateContent?alt=sse&key=${encodeURIComponent(apiKey)}`;

    try {
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);

      const initialResponse = await fetch(generateEndpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(requestPayload),
        signal: controller.signal,
      });

      clearTimeout(timeoutId);

      if (!initialResponse.ok) {
        continue;
      }

      const initialData = await initialResponse.json();
      const candidate = initialData.candidates?.[0];
      const parts = candidate?.content?.parts || [];
      const functionCallPart = parts.find(p => p.functionCall);

      if (functionCallPart && functionCallPart.functionCall) {
        const { name, args } = functionCallPart.functionCall;
        toolCallsExecuted.push({ name, args });

        const toolResult = await executeTool({
          toolName: name,
          args: args || {},
          authenticatedStudent,
          requestContext,
        });

        const secondPayload = {
          systemInstruction: requestPayload.systemInstruction,
          contents: [
            ...requestPayload.contents,
            {
              role: 'model',
              parts: [{ functionCall: { name, args: args || {} } }],
            },
            {
              role: 'function',
              parts: [
                {
                  functionResponse: {
                    name,
                    response: toolResult,
                  },
                },
              ],
            },
          ],
          generationConfig: requestPayload.generationConfig,
        };

        const streamRes = await fetch(streamEndpoint, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(secondPayload),
        });

        if (streamRes.ok) {
          let accumulatedReply = '';
          const reader = streamRes.body.getReader();
          const decoder = new TextDecoder('utf-8');
          let buffer = '';

          while (true) {
            const { done, value } = await reader.read();
            if (done) break;

            buffer += decoder.decode(value, { stream: true });
            const lines = buffer.split('\n');
            buffer = lines.pop();

            for (const line of lines) {
              const trimmed = line.trim();
              if (trimmed.startsWith('data: ')) {
                const jsonStr = trimmed.slice(6).trim();
                if (jsonStr === '[DONE]') continue;
                try {
                  const parsed = JSON.parse(jsonStr);
                  const chunk = parsed.candidates?.[0]?.content?.parts?.[0]?.text;
                  if (chunk) {
                    accumulatedReply += chunk;
                    if (onChunk) onChunk(chunk);
                  }
                } catch (jsonErr) {}
              }
            }
          }

          if (accumulatedReply.trim()) {
            return {
              reply: accumulatedReply.trim(),
              modelUsed: model,
              toolCallsExecuted,
            };
          }
        }
      } else {
        const text = parts[0]?.text;
        if (text && text.trim()) {
          const words = text.trim().split(' ');
          for (const w of words) {
            if (onChunk) onChunk(w + ' ');
            await new Promise(r => setTimeout(r, 12));
          }
          return {
            reply: text.trim(),
            modelUsed: model,
            toolCallsExecuted: [],
          };
        }
      }
    } catch (streamErr) {
      console.warn(`[GeminiService] Streaming error with ${model}: ${streamErr.message}`);
    }
  }

  const fallback = await generateFallbackResponseAsync(userMessage, verifiedContext, authenticatedStudent, requestContext);
  if (onChunk) onChunk(fallback);
  return { reply: fallback, modelUsed: 'deterministic-verified-context', toolCallsExecuted };
}

/**
 * Deterministic fallback generator strictly grounded in MongoDB tool executions.
 * Used if Gemini API key is missing or network times out.
 */
async function generateFallbackResponseAsync(userMessage, verifiedContext, authenticatedStudent, requestContext) {
  const msg = (userMessage || '').toLowerCase().trim();

  let firstName = authenticatedStudent?.studentName ? authenticatedStudent.studentName.split(' ')[0] : 'there';
  if (firstName === 'there' && verifiedContext) {
    const nameMatch = verifiedContext.match(/First Name:\s*([A-Za-z0-9_-]+)/i);
    if (nameMatch && nameMatch[1]) firstName = nameMatch[1];
  }

  const isGreeting = /^(hi|hello|hey|good\s+morning|good\s+afternoon|good\s+evening|gm|morning|evening|afternoon|howdy|sup|what'?s\s+up|namaste)\b/i.test(msg);
  if (isGreeting) {
    if (msg.includes('morning')) return `Good morning ${firstName}! Hope you're having a great day. What can I help you with today?`;
    if (msg.includes('afternoon')) return `Good afternoon ${firstName}! How can I help you today?`;
    if (msg.includes('evening')) return `Good evening ${firstName}! What would you like to order today?`;
    if (msg.startsWith('hi')) return `Hi ${firstName}! How can I help you today?`;
    return `Hello ${firstName}! How can I assist you with your campus meal today?`;
  }

  // QuickBite Support & Official FAQs handling
  if (msg.includes('support') || msg.includes('contact') || msg.includes('email') || msg.includes('help desk') || msg.includes('complaint')) {
    return `You can contact official QuickBite support at **quickbite.connecting@gmail.com**.\n` +
           `• Operating Hours: Mon–Sat 7:30 AM – 9:30 PM | Sun 8:00 AM – 8:00 PM\n` +
           `• Campus Canteens: Block 33 (Ground Floor) & Block 34 (1st Floor Food Court)\n` +
           `For active order assistance, you can also speak directly with counter staff.`;
  }

  if (msg.includes('cancel') || msg.includes('cancellation') || msg.includes('refund')) {
    return `**Order Cancellation Policy**:\n` +
           `• You can cancel your order while in **Placed** or **Waiting/Accepted** status before preparation begins.\n` +
           `• Once the kitchen status updates to **Preparing**, cancellation is locked to avoid food wastage.\n` +
           `• If you have any payment or food quality concerns, reach out to quickbite.connecting@gmail.com.`;
  }

  if (msg.includes('how to pick up') || msg.includes('how to collect') || msg.includes('how do i collect') || msg.includes('pickup instructions')) {
    return `**Pickup Instructions**:\n` +
           `1. Watch your order progress in the Order Tracking tab.\n` +
           `2. When status updates to **Ready for Pickup**, head to the designated canteen counter.\n` +
           `3. Present your digital **Token Number** to the counter staff to collect your meal!`;
  }

  if (msg.includes('your usual') || msg.includes('usual feature')) {
    return `**"Your Usual" Feature**:\n` +
           `QuickBite automatically tracks your most frequent campus orders so you can reorder your favorite meals with 1 tap right from your Home screen!`;
  }

  const isOrderRelated = msg.includes('order') || msg.includes('track') || msg.includes('token');
  const isCartRelated = msg.includes('cart') || msg.includes('checkout') || msg.includes('subtotal');

  const isSpecificDishQuery = !isOrderRelated && !isCartRelated && (
    msg.includes('available') ||
    msg.includes('price') ||
    msg.includes('cost') ||
    msg.includes('do you have') ||
    msg.includes('can i get') ||
    msg.includes('have you got') ||
    msg.includes('pancake') ||
    msg.includes('dosa') ||
    msg.includes('burger') ||
    msg.includes('sandwich') ||
    msg.includes('coffee') ||
    msg.includes('chole')
  );

  const dishInquiryMatch = !isOrderRelated && !isCartRelated && (
    msg.match(/(?:are|is|do you have|can i get|have you got|got any|price of|what is the price of|how much is|cost of)\s+([a-z0-9\s'-]+?)(?:\s+available|\s+today|\s+on the menu|\s+in canteen|\s+now|\?|$)/i) ||
    msg.match(/^([a-z0-9\s'-]+?)\s+(?:available|price|cost)\??$/i) ||
    (isSpecificDishQuery && msg.match(/\b(pancakes?|masala dosa|dosa|burger|pizza|sandwich|cold coffee|coffee|tea|chole bhature|chole|pasta|noodles|maggi|roll|biryani|paneer|idli|vada)\b/i))
  );

  if (dishInquiryMatch) {
    const candidateDish = (dishInquiryMatch[1] || dishInquiryMatch[0])
      .replace(/^(there|the|a|an)\s+/i, '')
      .replace(/^(are|is|do you have|can i get|what is the price of|price of)\s+/i, '')
      .trim();

    if (candidateDish && !['food', 'items', 'dishes', 'anything', 'something', 'order', 'orders', 'menu', 'cart', 'available'].includes(candidateDish.toLowerCase())) {
      const searchResult = await executeTool({
        toolName: 'search_food_item',
        args: {
          dishName: candidateDish,
          canteenId: requestContext.selectedCanteenId,
        },
        authenticatedStudent,
        requestContext,
      });

      if (searchResult.success) {
        return searchResult.message;
      }
    }
  }

  if (msg.includes('favourite') || msg.includes('favorite') || msg.includes('fav items') || msg.includes('saved items')) {
    const favResult = await executeTool({
      toolName: 'get_favourite_items',
      args: {},
      authenticatedStudent,
      requestContext,
    });
    if (favResult.success) {
      return favResult.message;
    }
    return favResult.error || "You haven't added any items to your favourites yet.";
  }

  if (msg.includes('recently') || msg.includes('recent order') || msg.includes('what did i order') || msg.includes('order history') || msg.includes('past order')) {
    const recentResult = await executeTool({
      toolName: 'get_recent_orders',
      args: { limit: 5 },
      authenticatedStudent,
      requestContext,
    });
    if (recentResult.success) {
      return recentResult.message;
    }
    return recentResult.error || "You haven't placed any orders yet.";
  }

  if (msg.includes('cart') || msg.includes('subtotal') || msg.includes('checkout')) {
    if (msg.includes('add') || msg.includes('put')) {
      let qty = 1;
      const qtyMatch = msg.match(/\b(\d+)\b/) || msg.match(/\b(two|three|four|five)\b/i);
      if (qtyMatch) {
        const wordMap = { two: 2, three: 3, four: 4, five: 5 };
        qty = wordMap[qtyMatch[1].toLowerCase()] || parseInt(qtyMatch[1], 10) || 1;
      }
      let dish = msg.replace(/add\s+(\d+|two|three|four|five)?\s*/i, '')
                    .replace(/\s+(to|in|into)\s+(my\s+)?cart/i, '')
                    .replace(/\s+to\s+my\s+cart/i, '')
                    .trim();
      if (!dish || dish === 'two' || dish === 'it') {
        dish = 'Masala Dosa';
      }
      const cartAction = await executeTool({
        toolName: 'manage_cart',
        args: { action: 'add', dishNameOrId: dish, quantity: qty },
        authenticatedStudent,
        requestContext,
      });
      return cartAction.message || cartAction.error;
    }

    if (msg.includes('clear') || msg.includes('empty')) {
      const clearResult = await executeTool({
        toolName: 'manage_cart',
        args: { action: 'clear' },
        authenticatedStudent,
        requestContext,
      });
      return clearResult.message;
    }

    const cartResult = await executeTool({
      toolName: 'get_cart_details',
      args: {},
      authenticatedStudent,
      requestContext,
    });

    if (cartResult.success && !cartResult.isEmpty && cartResult.items.length > 0) {
      const itemList = cartResult.items.map(it => `• ${it.name} x${it.quantity} (${it.subtotalFormatted})`).join('\n');
      return `Your cart has ${cartResult.itemCount} item${cartResult.itemCount > 1 ? 's' : ''} from ${cartResult.canteenName} (Total: ${cartResult.totalFormatted}):\n\n${itemList}`;
    }
    return "Your cart is currently empty. Feel free to explore the canteen menu to add dishes!";
  }

  const isOrderQuery = msg.includes('track') ||
                       msg.includes('order') ||
                       msg.includes('status') ||
                       msg.includes('ready') ||
                       msg.includes('where') ||
                       msg.includes('waiting') ||
                       msg.includes('second') ||
                       msg.includes('first') ||
                       msg.includes('both') ||
                       msg.includes('how much did i pay') ||
                       msg.includes('which canteen');

  if (isOrderQuery) {
    const idMatch = msg.match(/(?:order\s*(?:number\s*|no\.?\s*|#\s*)?|#)\s*([a-z0-9_-]{3,24})/i) ||
                    msg.match(/\b(ord-[0-9a-z_-]+)\b/i);
    const candidateId = idMatch ? idMatch[1] : null;

    if (candidateId && !['order', 'orders', 'status', 'ready', 'where', 'active', 'both', 'second', 'first'].includes(candidateId.toLowerCase())) {
      const trackingResult = await executeTool({
        toolName: 'get_order_tracking',
        args: { orderId: candidateId },
        authenticatedStudent,
        requestContext,
      });

      if (trackingResult.success) {
        const timelineSteps = (trackingResult.timeline?.steps || [])
          .map(s => `  ${s.state === 'COMPLETED' ? '✓' : (s.state === 'CURRENT' ? '➤' : '○')} ${s.name}${s.timestamp ? ` (${s.timestamp})` : ''}`)
          .join('\n');

        return `Order #${trackingResult.orderId} at ${trackingResult.canteenName}:\n` +
               `• Status: ${trackingResult.status} — ${trackingResult.statusExplanation}\n` +
               `• Token Number: #${trackingResult.tokenNumber}\n` +
               `• Items: ${trackingResult.itemsSummary} (Total: ${trackingResult.totalFormatted})\n` +
               `• Estimated Ready: ${trackingResult.estimatedReadyTime} (${trackingResult.pickupLocation})\n\n` +
               `Timeline:\n${timelineSteps}`;
      } else if (trackingResult.forbidden) {
        return trackingResult.error;
      } else if (trackingResult.found === false) {
        return trackingResult.error;
      }
    }

    const summary = await executeTool({
      toolName: 'get_order_status_summary',
      args: {},
      authenticatedStudent,
      requestContext,
    });

    if (summary.success) {
      if (summary.scenario === 'NO_ACTIVE_ORDERS') {
        return "You don't currently have any active orders. Feel free to browse the canteen menu to place an order!";
      }

      if (summary.scenario === 'SINGLE_ACTIVE_ORDER') {
        const o = summary.order;
        const timelineSteps = (o.timeline?.steps || [])
          .map(s => `  ${s.state === 'COMPLETED' ? '✓' : (s.state === 'CURRENT' ? '➤' : '○')} ${s.name}${s.timestamp ? ` (${s.timestamp})` : ''}`)
          .join('\n');

        return `Your order #${o.orderId} at ${o.canteenName} is currently **${o.status}**.\n` +
               `• ${o.statusExplanation}\n` +
               `• Token Number: #${o.tokenNumber}\n` +
               `• Items: ${o.items.join(', ')} (Total: ${o.totalFormatted})\n` +
               `• Estimated Ready Time: ${o.estimatedReadyTime} (${o.pickupLocation})\n\n` +
               `Timeline:\n${timelineSteps}`;
      }

      if (summary.scenario === 'MULTIPLE_ACTIVE_ORDERS') {
        // Check if student is asking for a specific order by reference (e.g. "second one", "first one", "both")
        if (msg.includes('second') || msg.includes('2nd') || msg.includes('order 2')) {
          const secondOrder = summary.orders[1] || summary.orders[0];
          const tracking = await executeTool({
            toolName: 'get_order_tracking',
            args: { orderId: secondOrder.orderId },
            authenticatedStudent,
            requestContext,
          });

          if (tracking.success) {
            return `Order 2 (#${tracking.orderId}) at ${tracking.canteenName} is currently **${tracking.status}**.\n` +
                   `• ${tracking.statusExplanation}\n` +
                   `• Token: #${tracking.tokenNumber} | Items: ${tracking.itemsSummary} | Total: ${tracking.totalFormatted}\n` +
                   `• Estimated Ready Time: ${tracking.estimatedReadyTime} (${tracking.pickupLocation})`;
          }
        }

        if (msg.includes('first') || msg.includes('1st') || msg.includes('order 1')) {
          const firstOrder = summary.orders[0];
          const tracking = await executeTool({
            toolName: 'get_order_tracking',
            args: { orderId: firstOrder.orderId },
            authenticatedStudent,
            requestContext,
          });

          if (tracking.success) {
            return `Order 1 (#${tracking.orderId}) at ${tracking.canteenName} is currently **${tracking.status}**.\n` +
                   `• ${tracking.statusExplanation}\n` +
                   `• Token: #${tracking.tokenNumber} | Items: ${tracking.itemsSummary} | Total: ${tracking.totalFormatted}\n` +
                   `• Estimated Ready Time: ${tracking.estimatedReadyTime} (${tracking.pickupLocation})`;
          }
        }

        // Default multiple active orders response
        const orderList = summary.orders.map((o, idx) =>
          `${idx + 1}. Order #${o.orderId} at ${o.canteenName}: **${o.status}** (${o.items.join(', ')}) — ${o.totalFormatted}`
        ).join('\n');

        return `You have ${summary.count} active orders currently in progress:\n\n${orderList}\n\nWhich order would you like to track in detail?`;
      }
    }

    return "You don't currently have any active orders. Feel free to browse the canteen menu to place an order!";
  }

  if (msg.includes('canteen') || msg.includes('open') || msg.includes('closed') || msg.includes('timing') || msg.includes('hours') || msg.includes('block')) {
    const canteenId = requestContext.selectedCanteenId || 'canteen_33';
    const canteenResult = await executeTool({
      toolName: 'get_canteen_details',
      args: { canteenId },
      authenticatedStudent,
      requestContext,
    });

    if (canteenResult.success) {
      const statusText = canteenResult.isOpen ? 'OPEN' : 'CLOSED';
      return `${canteenResult.name} in ${canteenResult.location} (${canteenResult.floor}) is currently **${statusText}**.\nOperating hours: ${canteenResult.operatingHours}.\nSpecialty: ${canteenResult.specialty || 'Fresh meals & snacks'}.`;
    }
  }

  if (msg.includes('menu') || msg.includes('food') || msg.includes('available') || msg.includes('eat') || msg.includes('dish') || msg.includes('recommend') || msg.includes('suggest') || msg.includes('under') || msg.includes('below') || msg.includes('cheap') || msg.includes('budget') || msg.includes('spicy') || msg.includes('snack') || msg.includes('breakfast') || msg.includes('healthy') || msg.includes('sweet') || msg.includes('quick')) {
    const canteenId = requestContext.selectedCanteenId || 'canteen_33';
    const menuResult = await executeTool({
      toolName: 'get_menu_items',
      args: { canteenId, availableOnly: true },
      authenticatedStudent,
      requestContext,
    });

    if (menuResult.success && menuResult.items.length > 0) {
      let filtered = menuResult.items;

      const priceMatch = msg.match(/(?:under|below|less than|within)\s*(?:rs\.?|inr|₹)?\s*(\d+)/i) ||
                          msg.match(/(?:rs\.?|inr|₹)\s*(\d+)/i);
      if (priceMatch) {
        const maxPrice = parseInt(priceMatch[1], 10);
        filtered = filtered.filter(i => i.price <= maxPrice);
      }

      // Dietary preference filtering
      if (msg.includes('breakfast') || msg.includes('morning')) {
        const bf = filtered.filter(i => (i.category || '').toLowerCase().includes('breakfast') || /(dosa|idli|vada|sandwich|coffee|tea|poha|omelette)/i.test(i.name));
        if (bf.length > 0) filtered = bf;
      } else if (msg.includes('healthy') || msg.includes('fitness')) {
        const healthy = filtered.filter(i => (i.category || '').toLowerCase().includes('healthy') || /(salad|juice|fruit|thali|soup|boiled|paneer)/i.test(i.name));
        if (healthy.length > 0) filtered = healthy;
      } else if (msg.includes('spicy')) {
        const spicy = filtered.filter(i => /(spicy|masala|chilli|schezwan|biryani|chole|curry)/i.test(i.name) || (i.description || '').toLowerCase().includes('spicy'));
        if (spicy.length > 0) filtered = spicy;
      } else if (msg.includes('sweet') || msg.includes('dessert')) {
        const sweet = filtered.filter(i => /(shake|ice cream|dessert|sweet|halwa|pastry|jamun|chocolate)/i.test(i.name));
        if (sweet.length > 0) filtered = sweet;
      } else if (msg.includes('quick') || msg.includes('fast') || msg.includes('rush')) {
        const quick = filtered.filter(i => {
          const m = (i.prepTime || '').match(/(\d+)/);
          return m && parseInt(m[1], 10) <= 10;
        });
        if (quick.length > 0) filtered = quick;
      }

      if (filtered.length > 0) {
        const topDishes = filtered.slice(0, 5).map(i => `• **${i.name}** - ${i.priceFormatted} (Rating: ${i.rating}⭐, ${i.prepTime})`).join('\n');
        return `Here are available items from ${menuResult.canteenName}${priceMatch ? ` under ₹${priceMatch[1]}` : ''}:\n\n${topDishes}\n\nTap on any item to view details or add it to your cart!`;
      } else if (priceMatch) {
        return `No items found under ₹${priceMatch[1]} at ${menuResult.canteenName}. The most affordable option is **${menuResult.items[0].name}** for ${menuResult.items[0].priceFormatted}.`;
      }
    }
  }

  if (msg.includes('review') || msg.includes('rating') || msg.includes('feedback')) {
    const reviewResult = await executeTool({
      toolName: 'get_food_reviews',
      args: { canteenId: requestContext.selectedCanteenId || 'canteen_33' },
      authenticatedStudent,
      requestContext,
    });

    if (reviewResult.success && reviewResult.reviews.length > 0) {
      const reviewText = reviewResult.reviews.slice(0, 3).map(r => `• "${r.comment}" (${r.rating}⭐ - ${r.userName})`).join('\n');
      return `Here are recent customer reviews for ${reviewResult.canteenId || 'this canteen'}:\n\n${reviewText}`;
    }
  }

  return "I'm here to help with live canteen menus, food recommendations, and order tracking. Would you like to check what's cooking right now or track your latest meal?";
}

module.exports = {
  generateAiResponse,
  streamAiResponse,
  generateFallbackResponseAsync,
};
