// AI Controller: Handles conversational queries via Gemini LLM with RAG context, streaming SSE, and user feedback
const jwt = require('jsonwebtoken');
const crypto = require('crypto');
const uuidv4 = () => crypto.randomUUID();
const { buildAiContext } = require('../services/aiContextService');
const { generateAiResponse, streamAiResponse } = require('../services/geminiService');
const AiFeedback = require('../models/AiFeedback');

const JWT_SECRET = process.env.JWT_SECRET || 'quickbite-super-secure-jwt-secret-key-2026';

// Extracts and validates student identity from JWT Bearer token with fallback to guest
function extractStudent(req) {
  let studentId = null;
  let studentEmail = '';
  let registrationNumber = '';
  let studentName = '';
  let isAuthenticated = false;

  const authHeader = req.headers['authorization'];
  if (authHeader && authHeader.startsWith('Bearer ')) {
    const token = authHeader.split(' ')[1];
    try {
      const decoded = jwt.verify(token, JWT_SECRET);
      studentId = decoded.uid;
      studentEmail = decoded.email || '';
      registrationNumber = decoded.registrationNumber || '';
      studentName = decoded.name || '';
      isAuthenticated = true;
    } catch (e) {
      isAuthenticated = false;
    }
  }

  if (!isAuthenticated) {
    studentId = 'guest_student';
    studentName = req.headers['x-student-name'] || req.body?.studentName || 'Student';
  }

  return { studentId, studentEmail, registrationNumber, studentName, isAuthenticated };
}

// Real-Time SSE Stream: Streams AI response tokens alongside contextual recommendations over Server-Sent Events
async function handleChatStream(req, res) {
  const startTime = Date.now();
  try {
    const { message, canteenId, conversationHistory } = req.body;

    if (!message || typeof message !== 'string' || message.trim() === '') {
      return res.status(400).json({ success: false, error: 'Message text is required.' });
    }

    res.setHeader('Content-Type', 'text/event-stream; charset=utf-8');
    res.setHeader('Cache-Control', 'no-cache, no-transform');
    res.setHeader('Connection', 'keep-alive');
    res.setHeader('X-Accel-Buffering', 'no');
    if (res.flushHeaders) res.flushHeaders();

    const { studentId, studentEmail, registrationNumber, studentName, isAuthenticated } = extractStudent(req);

    const authenticatedStudent = { studentId, studentEmail, registrationNumber, studentName, isAuthenticated };
    const requestContext = {
      selectedCanteenId: canteenId || '',
      cartItems: req.body?.cartItems || req.body?.cart || [],
      cartCanteenId: req.body?.cartCanteenId || canteenId || '',
    };

    const contextStartTime = Date.now();
    const { contextText, recommendedItems, activeOrders, trackedOrder, greetingData } = await buildAiContext({
      studentId,
      studentEmail,
      registrationNumber,
      studentName,
      message: message.trim(),
      selectedCanteenId: canteenId || '',
    });
    const contextDuration = Date.now() - contextStartTime;
    const messageId = uuidv4();

    const metaPayload = {
      type: 'meta',
      messageId,
      recommendedItems,
      activeOrders: activeOrders || [],
      trackedOrder: trackedOrder || null,
      greetingData: greetingData || null,
      contextDurationMs: contextDuration,
    };
    res.write(`data: ${JSON.stringify(metaPayload)}\n\n`);

    let firstTokenTime = null;
    let fullReply = '';

    const streamResult = await streamAiResponse({
      userMessage: message.trim(),
      verifiedContext: contextText,
      conversationHistory: conversationHistory || [],
      authenticatedStudent,
      requestContext,
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

    const donePayload = {
      type: 'done',
      messageId,
      fullReply: fullReply || streamResult.reply,
      modelUsed: streamResult.modelUsed,
      toolCallsExecuted: streamResult.toolCallsExecuted || [],
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

// Standard REST AI Chat: Generates single JSON response with grounding context and tool invocation
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

    const { studentId, studentEmail, registrationNumber, studentName, isAuthenticated } = extractStudent(req);

    const authenticatedStudent = { studentId, studentEmail, registrationNumber, studentName, isAuthenticated };
    const requestContext = {
      selectedCanteenId: canteenId || '',
      cartItems: req.body?.cartItems || req.body?.cart || [],
      cartCanteenId: req.body?.cartCanteenId || canteenId || '',
    };

    const { contextText, recommendedItems, activeOrders, trackedOrder, greetingData } = await buildAiContext({
      studentId,
      studentEmail,
      registrationNumber,
      studentName,
      message: message.trim(),
      selectedCanteenId: canteenId || '',
    });

    const { reply, modelUsed, toolCallsExecuted } = await generateAiResponse({
      userMessage: message.trim(),
      verifiedContext: contextText,
      conversationHistory: conversationHistory || [],
      authenticatedStudent,
      requestContext,
    });

    const messageId = uuidv4();
    const suggestions = generateContextualSuggestions(message, activeOrders);
    const durationMs = Date.now() - startTime;

    return res.json({
      success: true,
      messageId,
      reply,
      modelUsed,
      toolCallsExecuted: toolCallsExecuted || [],
      suggestions,
      recommendedItems,
      hasActiveOrders: activeOrders && activeOrders.length > 0,
      trackedOrder: trackedOrder || null,
      activeOrders: activeOrders || [],
      greetingData: greetingData || null,
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

// Feedback Recording: Records student thumbs-up/down ratings and comments for response evaluation
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

// Analytics: Computes satisfaction percentage and review summaries
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

// Generates contextual follow-up quick suggestion chips based on query intent
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
