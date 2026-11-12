const express = require('express');
const router = express.Router();
const jwt = require('jsonwebtoken');
const ProductSuggestion = require('../models/ProductSuggestion');
const Owner = require('../models/Owner');
const User = require('../models/User');
const { requireOwnerAuth } = require('./owners');

const JWT_SECRET = process.env.JWT_SECRET || 'canteen_super_secret_jwt_key_2024';
const COOLDOWN_MS = 3 * 24 * 60 * 60 * 1000; // 3 Days (72 Hours)

/**
 * Helper to collect all potential student identifiers for strict isolation & rate limiting.
 */
async function resolveStudentIdentities(req) {
  const identities = new Set();
  const rawId = req.headers['x-student-id'] || req.body?.studentId;
  const rawReg = req.headers['x-student-reg'] || req.body?.studentRegistration;
  const rawEmail = (req.headers['x-student-email'] || req.body?.studentEmail || '').trim().toLowerCase();

  if (rawId) identities.add(String(rawId).trim());
  if (rawReg) identities.add(String(rawReg).trim());
  if (rawEmail) identities.add(rawEmail);

  // Check JWT if present
  const authHeader = req.headers['authorization'] || '';
  if (authHeader.startsWith('Bearer ')) {
    try {
      const decoded = jwt.verify(authHeader.slice(7), JWT_SECRET);
      if (decoded?.uid) identities.add(String(decoded.uid));
      if (decoded?.registrationNumber) identities.add(String(decoded.registrationNumber));
      if (decoded?.email) identities.add(String(decoded.email).trim().toLowerCase());
    } catch (_) {
      // Non-fatal if JWT is expired/invalid; fallback to headers
    }
  }

  // Also query MongoDB User to expand registration number / email if any ID matched
  if (identities.size > 0) {
    try {
      const idArray = Array.from(identities);
      const user = await User.findOne({
        $or: [
          { uid: { $in: idArray } },
          { registrationNumber: { $in: idArray } },
          { email: { $in: idArray } },
        ],
      }).lean();

      if (user) {
        if (user.uid) identities.add(String(user.uid));
        if (user.registrationNumber) identities.add(String(user.registrationNumber));
        if (user.email) identities.add(String(user.email).trim().toLowerCase());
      }
    } catch (e) {
      console.warn('[Suggestions] Error expanding user identities:', e.message);
    }
  }

  return Array.from(identities);
}

// ── 1. POST /api/suggestions - Submit Product Suggestion with 3-Day Cooldown ──
router.post('/', async (req, res) => {
  try {
    const { canteenId, canteenName, suggestionText, studentName } = req.body;

    if (!canteenId || !canteenId.trim()) {
      return res.status(400).json({ success: false, error: 'Canteen ID is required.' });
    }

    const trimmedText = (suggestionText || '').trim();
    if (!trimmedText || trimmedText.length < 2) {
      return res.status(400).json({ success: false, error: 'Please enter a product suggestion.' });
    }

    if (trimmedText.length > 1000) {
      return res.status(400).json({ success: false, error: 'Suggestion text cannot exceed 1000 characters.' });
    }

    const identities = await resolveStudentIdentities(req);
    const primaryStudentId = req.headers['x-student-reg'] || req.headers['x-student-id'] || req.body.studentId || identities[0] || 'anonymous';
    const studentEmail = (req.headers['x-student-email'] || req.body.studentEmail || '').trim().toLowerCase();
    const studentReg = req.headers['x-student-reg'] || req.body.studentRegistration || '';
    const resolvedStudentName = req.headers['x-student-name'] || studentName || 'Student';

    // ── 3-Day Rate Limiting Check ──────────────────────────────────────────
    const threeDaysAgo = new Date(Date.now() - COOLDOWN_MS);
    const query = {
      canteenId: canteenId.trim(),
      createdAt: { $gte: threeDaysAgo },
    };

    if (identities.length > 0) {
      query.$or = [
        { studentId: { $in: identities } },
        ...(studentEmail ? [{ studentEmail }] : []),
      ];
    } else {
      query.studentId = primaryStudentId;
    }

    const recentSuggestion = await ProductSuggestion.findOne(query).sort({ createdAt: -1 }).lean();

    if (recentSuggestion) {
      const elapsedMs = Date.now() - new Date(recentSuggestion.createdAt).getTime();
      const remainingMs = Math.max(0, COOLDOWN_MS - elapsedMs);
      const remainingHours = Math.ceil(remainingMs / (1000 * 60 * 60));
      const remainingDays = Math.ceil(remainingHours / 24);
      const nextAllowedAt = new Date(new Date(recentSuggestion.createdAt).getTime() + COOLDOWN_MS).toISOString();

      return res.status(429).json({
        success: false,
        code: 'RATE_LIMIT_COOLDOWN',
        error: `You have already submitted a suggestion for this canteen recently. Please wait ${
          remainingDays > 1 ? remainingDays + ' days' : remainingHours + ' hours'
        } before submitting another.`,
        remainingHours,
        nextAllowedAt,
      });
    }

    // ── Route strictly to the canteen's assigned owner ────────────────────
    const owner = await Owner.findOne({
      canteenId: canteenId.trim(),
      canteenAssigned: true,
    }).lean();

    const ownerId = owner ? owner.uid : null;

    const newSuggestion = await ProductSuggestion.create({
      suggestionId: `sug_${Date.now()}_${Math.floor(Math.random() * 9000 + 1000)}`,
      canteenId: canteenId.trim(),
      canteenName: canteenName || (owner ? owner.name : '') || canteenId.trim(),
      studentId: primaryStudentId,
      studentName: resolvedStudentName,
      studentEmail,
      studentRegistration: studentReg,
      ownerId,
      suggestionText: trimmedText,
      status: 'New',
    });

    console.log(
      `[Suggestions] Successfully recorded suggestion ${newSuggestion.suggestionId} for canteen ${canteenId} (routed to owner: ${ownerId || 'unassigned'})`
    );

    return res.status(201).json({
      success: true,
      message: 'Thank you! Your product suggestion has been sent to the canteen owner.',
      suggestion: newSuggestion,
    });
  } catch (err) {
    console.error('[Suggestions] Error submitting suggestion:', err);
    return res.status(500).json({
      success: false,
      error: 'Failed to submit product suggestion. Please try again.',
    });
  }
});

// ── 2. GET /api/suggestions/eligibility - Check if user is eligible to submit ──
router.get('/eligibility', async (req, res) => {
  try {
    const canteenId = (req.query.canteenId || '').trim();
    if (!canteenId) {
      return res.status(400).json({ success: false, error: 'canteenId query param is required.' });
    }

    const identities = await resolveStudentIdentities(req);
    const threeDaysAgo = new Date(Date.now() - COOLDOWN_MS);
    const query = {
      canteenId,
      createdAt: { $gte: threeDaysAgo },
    };

    if (identities.length > 0) {
      query.$or = [
        { studentId: { $in: identities } },
      ];
    } else {
      return res.json({ success: true, eligible: true, remainingHours: 0 });
    }

    const recentSuggestion = await ProductSuggestion.findOne(query).sort({ createdAt: -1 }).lean();

    if (recentSuggestion) {
      const elapsedMs = Date.now() - new Date(recentSuggestion.createdAt).getTime();
      const remainingMs = Math.max(0, COOLDOWN_MS - elapsedMs);
      const remainingHours = Math.ceil(remainingMs / (1000 * 60 * 60));
      const nextAllowedAt = new Date(new Date(recentSuggestion.createdAt).getTime() + COOLDOWN_MS).toISOString();

      return res.json({
        success: true,
        eligible: false,
        remainingHours,
        nextAllowedAt,
        message: `Cooldown active. You can submit another suggestion in ${remainingHours} hours.`,
      });
    }

    return res.json({
      success: true,
      eligible: true,
      remainingHours: 0,
      nextAllowedAt: null,
    });
  } catch (err) {
    console.error('[Suggestions] Error checking eligibility:', err);
    return res.status(500).json({ success: false, error: err.message });
  }
});

// ── 3. GET /api/suggestions/owner - Fetch Suggestions for Authenticated Owner ──
router.get('/owner', requireOwnerAuth, async (req, res) => {
  try {
    const ownerCanteenId = req.owner.canteenId;
    if (!ownerCanteenId) {
      return res.status(400).json({ success: false, error: 'No canteen assigned to this owner.' });
    }

    const suggestions = await ProductSuggestion.find({ canteenId: ownerCanteenId })
      .sort({ createdAt: -1 })
      .limit(100)
      .lean();

    return res.json({
      success: true,
      canteenId: ownerCanteenId,
      count: suggestions.length,
      suggestions,
    });
  } catch (err) {
    console.error('[Suggestions] Error fetching owner suggestions:', err);
    return res.status(500).json({ success: false, error: 'Failed to fetch suggestions.' });
  }
});

// ── 4. PATCH /api/suggestions/owner/:suggestionId/status - Owner Status Update ──
router.patch('/owner/:suggestionId/status', requireOwnerAuth, async (req, res) => {
  try {
    const { suggestionId } = req.params;
    const { status, ownerNote } = req.body;

    const validStatuses = ['New', 'Under Review', 'Accepted', 'Rejected', 'Added to Menu'];
    if (!status || !validStatuses.includes(status)) {
      return res.status(400).json({
        success: false,
        error: `Invalid status. Must be one of: ${validStatuses.join(', ')}`,
      });
    }

    const suggestion = await ProductSuggestion.findOne({
      suggestionId,
      canteenId: req.owner.canteenId,
    });

    if (!suggestion) {
      return res.status(404).json({
        success: false,
        error: 'Suggestion not found or does not belong to your assigned canteen.',
      });
    }

    suggestion.status = status;
    if (ownerNote !== undefined) {
      suggestion.ownerNote = String(ownerNote).trim().slice(0, 500);
    }
    suggestion.updatedAt = new Date();

    await suggestion.save();

    console.log(`[Suggestions] Owner updated suggestion ${suggestionId} status to ${status}`);

    return res.json({
      success: true,
      message: `Suggestion marked as "${status}".`,
      suggestion,
    });
  } catch (err) {
    console.error('[Suggestions] Error updating suggestion status:', err);
    return res.status(500).json({ success: false, error: 'Failed to update suggestion status.' });
  }
});

module.exports = router;
