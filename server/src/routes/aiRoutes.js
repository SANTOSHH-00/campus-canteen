/**
 * AI Assistant API Routes
 * Mounts endpoints for QuickBite Gemini AI chat, streaming, feedback, and contextual suggestions.
 */

const express = require('express');
const router = express.Router();
const aiController = require('../controllers/aiController');

// POST /api/ai/stream - Process student query with token/chunk STREAMING over SSE (~5s total, 1-2s first chunk)
router.post('/stream', aiController.handleChatStream);

// POST /api/ai/chat - Process student conversational query with Gemini + MongoDB grounding (synchronous)
router.post('/chat', aiController.handleChat);

// POST /api/ai/feedback - Record thumbs-up / thumbs-down evaluation feedback
router.post('/feedback', aiController.handleFeedback);

// GET /api/ai/feedback/analytics - Retrieve aggregated feedback quality metrics
router.get('/feedback/analytics', aiController.handleFeedbackAnalytics);

// GET /api/ai/suggestions - Get starter suggestion chips for the welcome screen
router.get('/suggestions', aiController.getInitialSuggestions);

module.exports = router;
