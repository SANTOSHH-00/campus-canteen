/**
 * AI Controller for QuickBite Assistant (MunchMate)
 * Coordinates authentication, context generation, Gemini AI streaming interaction,
 * and user feedback collection (thumbs-up / thumbs-down) in MongoDB.
 */

const jwt = require('jsonwebtoken');
const crypto = require('crypto');
const uuidv4 = () => crypto.randomUUID();
const { buildAiContext } = require('../services/aiContextService');
const { generateAiResponse, streamAiResponse } = require('../services/geminiService');
const AiFeedback = require('../models/AiFeedback');

const JWT_SECRET = process.env.JWT_SECRET || 'quickbite-super-secure-jwt-secret-key-2026';

/**
 * Extracts and verifies authenticated student from request.
 */
function extractStudent(req) {
  let studentId = null;
  let studentEmail = '';

  const authHeader = req.headers['authorization'];
  if (authHeader && authHeader.startsWith('Bearer ')) {
    const token = authHeader.split(' ')[1];
    try {
      const decoded = jwt.verify(token, JWT_SECRET);
      studentId = decoded.uid;
      studentEmail = decoded.email || '';
    } catch (e) {
      // Token invalid / expired
    }
  }

  if (!studentId) {
    studentId = req.headers['x-student-id'] || req.body?.studentId || 'guest_student';
  }

  return { studentId, studentEmail };
}

/**
 * Handles incoming chat messages with token/chunk STREAMING over Server-Sent Events (SSE).
 * Target response time: ~5s total, first chunk in 1-2s.
 * POST /api/ai/stream
 */
async function handleChatStream(req, res) {
  const startTime = Date.now();
  try {
    const { message, canteenId, conversationHistory } = req.body;

    if (!message || typeof message !== 'string' || message.trim() === '') {
      return res.status(400).json({ success: false, error: 'Message text is required.' });
    }

    // Set SSE headers
    res.setHeader('Content-Type', 'text/event-stream; charset=utf-8');
    res.setHeader('Cache-Control', 'no-cache, no-transform');
    res.setHeader('Connection', 'keep-alive');
    res.setHeader('X-Accel-Buffering', 'no'); // Disable proxy buffering (Nginx, Render)
    if (res.flushHeaders) res.flushHeaders();

    const { studentId } = extractStudent(req);

    // 1. Build verified MongoDB facts (fast parallel + cached)
    const contextStartTime = Date.now();
    const { contextText, recommendedItems, activeOrders, trackedOrder } = await buildAiContext({
      studentId,
      message: message.trim(),
      selectedCanteenId: canteenId || '',
    });
    const contextDuration = Date.now() - contextStartTime;

    const messageId = uuidv4();

    // 2. Emit initial metadata immediately so client can render order cards / items
    const metaPayload = {
      type: 'meta',
      messageId,
      recommendedItems,
      activeOrders: activeOrders || [],
      trackedOrder: trackedOrder || null,
      contextDurationMs: contextDuration,
    };
    res.write(`data: ${JSON.stringify(metaPayload)}\n\n`);

    // 3. Stream Gemini response chunks
    let firstTokenTime = null;
    let fullReply = '';

    const streamResult = await streamAiResponse({
      userMessage: message.trim(),
      verifiedContext: contextText,
      conversationHistory: conversationHistory || [],
      onChunk: (chunk) => {
        if (!firstTokenTime) {
          firstTokenTime = Date.now() - startTime;
        }
        fullReply += chunk;
        res.write(`data: ${JSON.stringify({ type: 'chunk', text: chunk })}\n\n`);
      },
    });

    const suggestions = generateContextualSuggestions(message, activeOrders);
    const totalDuration = Date.now() - startTime;

    // 4. Emit done event
    const donePayload = {
      type: 'done',
      messageId,
      fullReply: fullReply || streamResult.reply,
      modelUsed: streamResult.modelUsed,
      suggestions,
      firstTokenMs: firstTokenTime || totalDuration,
      totalDurationMs: totalDuration,
    };
    res.write(`data: ${JSON.stringify(donePayload)}\n\n`);
    res.end();
  } catch (err) {
    console.error('[AIController] Stream error:', err);
    if (!res.headersSent) {
      return res.status(500).json({ success: false, error: 'Streaming failed.' });
    }
    res.write(`data: ${JSON.stringify({ type: 'error', error: 'Assistant encountered an error while streaming.' })}\n\n`);
    res.end();
  }
}

/**
 * Handles incoming chat messages from the Android AI Assistant (Standard non-streaming JSON).
 * POST /api/ai/chat
 */
async function handleChat(req, res) {
  const startTime = Date.now();
  try {
    const { message, canteenId, conversationHistory } = req.body;

    if (!message || typeof message !== 'string' || message.trim() === '') {
      return res.status(400).json({
        success: false,
        error: 'Message text is required.',
      });
    }

    const { studentId } = extractStudent(req);

    // 1. Retrieve verified MongoDB facts & candidate menu items & tracking details
    const { contextText, recommendedItems, activeOrders, trackedOrder } = await buildAiContext({
      studentId,
      message: message.trim(),
      selectedCanteenId: canteenId || '',
    });

    // 2. Generate grounded Gemini response
    const { reply, modelUsed } = await generateAiResponse({
      userMessage: message.trim(),
      verifiedContext: contextText,
      conversationHistory: conversationHistory || [],
    });

    // 3. Generate unique messageId for feedback tracking
    const messageId = uuidv4();

    // 4. Generate contextual follow-up quick suggestion chips
    const suggestions = generateContextualSuggestions(message, activeOrders);
    const durationMs = Date.now() - startTime;

    return res.json({
      success: true,
      messageId,
      reply,
      modelUsed,
      suggestions,
      recommendedItems,
      hasActiveOrders: activeOrders && activeOrders.length > 0,
      trackedOrder: trackedOrder || null,
      activeOrders: activeOrders || [],
      durationMs,
    });
  } catch (err) {
    console.error('[AIController] Error processing chat request:', err);
    return res.status(500).json({
      success: false,
      error: 'MunchMate is temporarily unavailable. Please try again shortly.',
      details: process.env.NODE_ENV !== 'production' ? err.message : undefined,
    });
  }
}

/**
 * Submits or updates thumbs-up / thumbs-down feedback for an AI response.
 * POST /api/ai/feedback
 */
async function handleFeedback(req, res) {
  try {
    const { messageId, userQuestion, assistantResponse, feedbackType, reason } = req.body;

    if (!messageId || typeof messageId !== 'string') {
      return res.status(400).json({
        success: false,
        error: 'messageId is required to associate feedback.',
      });
    }

    if (!feedbackType || !['positive', 'negative'].includes(feedbackType.toLowerCase())) {
      return res.status(400).json({
        success: false,
        error: 'feedbackType must be either "positive" or "negative".',
      });
    }

    const { studentId, studentEmail } = extractStudent(req);
    const typeNormalized = feedbackType.toLowerCase();

    // Upsert feedback: update existing rating if student changes their mind, or create new
    const feedbackDoc = await AiFeedback.findOneAndUpdate(
      { studentId, messageId },
      {
        $set: {
          feedbackId: uuidv4(),
          studentId,
          studentEmail,
          messageId,
          userQuestion: userQuestion ? String(userQuestion).slice(0, 1000) : '',
          assistantResponse: assistantResponse ? String(assistantResponse).slice(0, 2000) : '',
          feedbackType: typeNormalized,
          reason: reason ? String(reason).slice(0, 500) : '',
        },
      },
      { upsert: true, new: true, setDefaultsOnInsert: true }
    );

    console.info(`[AIController] Feedback recorded: student=${studentId}, message=${messageId}, type=${typeNormalized}`);

    return res.json({
      success: true,
      message: 'Feedback recorded successfully. Thank you for helping MunchMate improve!',
      feedback: {
        id: feedbackDoc.feedbackId,
        messageId: feedbackDoc.messageId,
        feedbackType: feedbackDoc.feedbackType,
        updatedAt: feedbackDoc.updatedAt,
      },
    });
  } catch (err) {
    console.error('[AIController] Error recording feedback:', err);
    return res.status(500).json({
      success: false,
      error: 'Failed to record feedback.',
      details: process.env.NODE_ENV !== 'production' ? err.message : undefined,
    });
  }
}

/**
 * Returns analytical feedback aggregations for evaluation and continuous quality improvement.
 * GET /api/ai/feedback/analytics
 */
async function handleFeedbackAnalytics(req, res) {
  try {
    const total = await AiFeedback.countDocuments();
    const positive = await AiFeedback.countDocuments({ feedbackType: 'positive' });
    const negative = await AiFeedback.countDocuments({ feedbackType: 'negative' });

    const recentNegative = await AiFeedback.find({ feedbackType: 'negative' })
      .sort({ createdAt: -1 })
      .limit(20)
      .select('userQuestion assistantResponse reason createdAt');

    const satisfactionRate = total > 0 ? ((positive / total) * 100).toFixed(1) : '100.0';

    return res.json({
      success: true,
      analytics: {
        totalFeedbackCount: total,
        positiveCount: positive,
        negativeCount: negative,
        satisfactionRatePercent: Number(satisfactionRate),
        recentNegativeFeedback: recentNegative,
      },
    });
  } catch (err) {
    console.error('[AIController] Error retrieving feedback analytics:', err);
    return res.status(500).json({
      success: false,
      error: 'Failed to retrieve feedback analytics.',
    });
  }
}

/**
 * Generate smart suggestion chips based on what the student just asked
 */
function generateContextualSuggestions(userMessage, activeOrders) {
  const query = (userMessage || '').toLowerCase();

  if (query.includes('order') || query.includes('track') || query.includes('ready')) {
    return [
      "Find food under ₹100",
      "What's popular today?",
      "Explore available canteens",
    ];
  }

  if (query.includes('recommend') || query.includes('eat') || query.includes('food') || query.includes('price')) {
    return [
      "Track my latest order",
      "Find food under ₹100",
      "What's popular today?",
    ];
  }

  return [
    "Recommend something tasty",
    "What's popular today?",
    "Find food under ₹100",
    "Where is my order?",
  ];
}

function getInitialSuggestions(req, res) {
  return res.json({
    success: true,
    suggestions: [
      "Recommend something tasty",
      "What's popular today?",
      "Find food under ₹100",
      "Track my latest order",
      "Check my order status",
      "Explore available canteens",
    ],
  });
}

module.exports = {
  handleChat,
  handleChatStream,
  handleFeedback,
  handleFeedbackAnalytics,
  getInitialSuggestions,
};
