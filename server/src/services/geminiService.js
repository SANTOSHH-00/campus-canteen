/**
 * Gemini Service for QuickBite – MunchMate
 * Ultra-fast response streaming (< 5 seconds target, first chunk in 1-2s),
 * model fallback cascade, strict timeouts, zero branding leak, authoritative accuracy,
 * personalised greetings, and robust multi-turn alternation.
 */

const crypto = require('crypto');

const GEMINI_API_BASE = 'https://generativelanguage.googleapis.com/v1beta/models';

// Preferred production models based on verified Google Gemini API availability:
const DEFAULT_MODEL = process.env.GEMINI_MODEL || 'gemini-3.5-flash';
const FALLBACK_MODELS = [
  'gemini-3.5-flash',
  'gemini-flash-latest',
  'gemini-3.8-flash',
  'gemini-3.7-flash',
  'gemini-pro-latest',
];
const REQUEST_TIMEOUT_MS = 10000;

const SYSTEM_INSTRUCTION = `You are MunchMate, the official AI food-ordering assistant for the QuickBite campus canteen app ("MunchMate by QuickBite").
Your purpose is to assist students with live food recommendations, canteen menus, accurate order tracking, order history, and QuickBite questions.

STRICT OPERATIONAL RULES:
1. Identity: You are MunchMate by QuickBite. Never refer to yourself as Gemini, Google AI, or any other LLM.
2. 100% Data Accuracy: Base all food items, prices, canteen names, order statuses, and estimated times STRICTLY on the verified application data in the context.
3. Zero Hallucination: NEVER invent food items, prices, order IDs, payment status, or pickup times. If data is not provided, state that you don't have that information.
4. Personalised Greetings:
   - When the student sends a greeting (e.g. "Hi", "Hello", "Hey", "Good morning", "Good afternoon", "Good evening"):
     Respond with a warm, personalised greeting using the student's first name and appropriate time greeting:
     * Morning: "Good morning [First Name]! Hope you're having a great day. What can I help you with?"
     * Afternoon: "Good afternoon [First Name]! How can I help you?"
     * Evening: "Good evening [First Name]! What can I help you with?"
     * Casual: "Hi [First Name]! How can I help you today?" or "Hello [First Name]! What can I help you with?"
   - For follow-up questions during an ongoing conversation, do NOT repeat greetings unnecessarily. Answer directly and naturally.
5. Order Tracking Flow:
   - Look at the "ACTIVE ORDERS FOR THIS STUDENT" section in the verified data.
   - If active orders exist:
     * Report the exact status explanation given in the verified data.
     * For "NEW", "WAITING", "PLACED", or "PENDING": state "Your order #[orderId] is currently waiting for the canteen to accept it."
     * For "CONFIRMED", "ACCEPTED", or "PREPARING": state "Your order #[orderId] has been accepted and is currently being prepared by the canteen."
     * For "READY" or "READY_FOR_PICKUP": state "Your order #[orderId] is ready for pickup! Show your token #[tokenNumber] at the counter."
     * When the student has multiple active orders, summarize them and ask which specific order they want to track.
   - If the student has NO active orders in the database, say: "You don't have any active orders right now."
6. Multi-turn Context & Follow-ups:
   - Understand follow-up questions referencing previous answers (e.g., "is it spicy?", "which one is cheapest?", "tell me more about the first item").
   - Maintain conversational context while strictly adhering to verified facts.
7. Security & Privacy:
   - You only have access to the authenticated student's data. If an order ID belongs to someone else or is invalid, say: "For privacy and security, I can only provide details for your own orders."
8. Tone & Style:
   - Friendly, warm, concise, and helpful. Avoid verbose essays. Answer directly with clarity.`;

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

    // Discard any turn that doesn't alternate
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

  // Ensure the history ends with 'model' so the new user message can be cleanly appended with role 'user'
  if (cleanTurns.length > 0 && cleanTurns[cleanTurns.length - 1].role === 'user') {
    cleanTurns.pop();
  }

  // Keep last 4 turns for low latency and focused context
  return cleanTurns.slice(-4);
}

function buildPayload(userMessage, verifiedContext, conversationHistory) {
  const cleanHistory = sanitizeConversationHistory(conversationHistory);

  const promptWithContext = `=== VERIFIED QUICKBITE APPLICATION DATA ===
${verifiedContext || 'No specific database records matched.'}
===========================================

STUDENT MESSAGE:
${userMessage}`;

  return {
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
      temperature: 0.3,
      topK: 30,
      topP: 0.9,
      maxOutputTokens: 512, // Compact tokens for rapid <= 5s generation
    },
  };
}

/**
 * Streams AI response chunk-by-chunk using Gemini streamGenerateContent (SSE).
 * First token appears in 1-2 seconds.
 */
async function streamAiResponse({ userMessage, verifiedContext, conversationHistory = [], onChunk }) {
  const apiKey = process.env.GEMINI_API_KEY;

  if (!apiKey || apiKey.trim() === '' || apiKey === 'your-gemini-api-key') {
    console.warn('[GeminiService] GEMINI_API_KEY not configured. Streaming deterministic fallback.');
    const fallbackText = generateFallbackResponse(userMessage, verifiedContext);
    const words = fallbackText.split(' ');
    for (const w of words) {
      if (onChunk) onChunk(w + ' ');
      await new Promise(r => setTimeout(r, 20));
    }
    return { reply: fallbackText, modelUsed: 'deterministic-verified-context' };
  }

  const modelCandidateList = [
    process.env.GEMINI_MODEL || DEFAULT_MODEL,
    ...FALLBACK_MODELS,
  ].filter((m, i, arr) => arr.indexOf(m) === i);

  const requestPayload = buildPayload(userMessage, verifiedContext, conversationHistory);

  for (const model of modelCandidateList) {
    const endpoint = `${GEMINI_API_BASE}/${encodeURIComponent(model)}:streamGenerateContent?alt=sse&key=${encodeURIComponent(apiKey)}`;

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
        const errBody = await response.text();
        console.warn(`[GeminiService] Stream model ${model} returned status ${response.status}: ${errBody.slice(0, 150)}`);
        continue;
      }

      let accumulatedReply = '';
      const reader = response.body.getReader();
      const decoder = new TextDecoder('utf-8');
      let buffer = '';

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        buffer = lines.pop(); // keep last incomplete line

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
                if (onChunk) {
                  onChunk(chunk);
                }
              }
            } catch (jsonErr) {
              // Ignore unparseable SSE ping/chunk
            }
          }
        }
      }

      if (accumulatedReply.trim()) {
        return {
          reply: accumulatedReply.trim(),
          modelUsed: model,
        };
      }
    } catch (streamErr) {
      console.warn(`[GeminiService] Streaming error with ${model}: ${streamErr.message}`);
    }
  }

  console.warn('[GeminiService] All stream candidates failed. Falling back to deterministic generator.');
  const fallback = generateFallbackResponse(userMessage, verifiedContext);
  if (onChunk) onChunk(fallback);
  return { reply: fallback, modelUsed: 'deterministic-verified-context' };
}

/**
 * Standard non-streaming request (returns complete reply).
 */
async function generateAiResponse({ userMessage, verifiedContext, conversationHistory = [] }) {
  const apiKey = process.env.GEMINI_API_KEY;

  if (!apiKey || apiKey.trim() === '' || apiKey === 'your-gemini-api-key') {
    return {
      reply: generateFallbackResponse(userMessage, verifiedContext),
      modelUsed: 'deterministic-verified-context',
    };
  }

  const modelCandidateList = [
    process.env.GEMINI_MODEL || DEFAULT_MODEL,
    ...FALLBACK_MODELS,
  ].filter((m, i, arr) => arr.indexOf(m) === i);

  const requestPayload = buildPayload(userMessage, verifiedContext, conversationHistory);

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
        continue;
      }

      const data = await response.json();
      const replyText = data.candidates?.[0]?.content?.parts?.[0]?.text;

      if (replyText && replyText.trim() !== '') {
        return {
          reply: replyText.trim(),
          modelUsed: model,
        };
      }
    } catch (e) {
      console.warn(`[GeminiService] Error with ${model}: ${e.message}`);
    }
  }

  return {
    reply: generateFallbackResponse(userMessage, verifiedContext),
    modelUsed: 'deterministic-verified-context',
  };
}

/**
 * Deterministic fallback generator grounded in verified MongoDB context.
 */
function generateFallbackResponse(userMessage, verifiedContext) {
  const msg = (userMessage || '').toLowerCase().trim();

  // Extract student first name from verified context if available
  let firstName = 'there';
  const nameMatch = verifiedContext.match(/First Name:\s*([A-Za-z0-9_-]+)/i);
  if (nameMatch && nameMatch[1]) {
    firstName = nameMatch[1];
  }

  // Extract time of day greeting
  let timeGreeting = 'Hello';
  const greetingMatch = verifiedContext.match(/Local Greeting:\s*"([^"]+)"/i);
  if (greetingMatch && greetingMatch[1]) {
    timeGreeting = greetingMatch[1];
  }

  // 1. GREETING HANDLER
  const isGreeting = /^(hi|hello|hey|good\s+morning|good\s+afternoon|good\s+evening|gm|morning|evening|afternoon|howdy|sup|what'?s\s+up|namaste)\b/i.test(msg);
  if (isGreeting) {
    if (msg.includes('morning')) {
      return `Good morning ${firstName}! Hope you're having a great day. What can I help you with?`;
    }
    if (msg.includes('afternoon')) {
      return `Good afternoon ${firstName}! How can I help you?`;
    }
    if (msg.includes('evening')) {
      return `Good evening ${firstName}! What can I help you with?`;
    }
    if (msg.startsWith('hi')) {
      return `Hi ${firstName}! How can I help you today?`;
    }
    return `Hello ${firstName}! What can I help you with?`;
  }

  // 2. ORDER TRACKING HANDLER
  if (msg.includes('track') || msg.includes('order') || msg.includes('where') || msg.includes('status') || msg.includes('ready') || msg.includes('waiting')) {
    if (verifiedContext && verifiedContext.includes('ACTIVE ORDERS FOR THIS STUDENT')) {
      const activeMatch = verifiedContext.match(/• Order ID: #([A-Za-z0-9_-]+)[\s\S]*?Status Explanation:\s*([^\n]+)/i);
      if (activeMatch) {
        const [_, orderId, statusExplanation] = activeMatch;
        return statusExplanation;
      }
    }
    return "You don't currently have any active orders. You can explore the menu to place a new order!";
  }

  // 3. MENU RECOMMENDATION HANDLER
  if (msg.includes('recommend') || msg.includes('popular') || msg.includes('tasty') || msg.includes('food')) {
    return "Here are top popular recommendations from our campus canteens! You can tap on any card below to view details and add it to your cart.";
  }

  return `I'm MunchMate, your QuickBite assistant! I can help you check canteen menus, track your orders, or find delicious food on campus. How can I assist you today?`;
}

module.exports = {
  generateAiResponse,
  streamAiResponse,
  sanitizeConversationHistory,
  DEFAULT_MODEL,
};
