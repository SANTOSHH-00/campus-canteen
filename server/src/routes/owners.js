const express = require('express');
const router = express.Router();
const crypto = require('crypto');
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const Owner = require('../models/Owner');
const User = require('../models/User');
const Canteen = require('../models/Canteen');
const OtpVerification = require('../models/OtpVerification');
const { sendOwnerOTP, sendAdminPortalOTP } = require('../services/emailService');

const JWT_SECRET = process.env.JWT_SECRET || 'quickbite-super-secure-jwt-secret-key-2026';

// Helper: Hash OTP with SHA-256
function hashOtp(otp) {
  return crypto.createHash('sha256').update(otp.trim()).digest('hex');
}

// ── POST /api/owner/login (Owner Email & Password -> Sends 6-digit OTP) ─────
router.post('/login', async (req, res) => {
  try {
    const { email, password } = req.body;
    if (!email || !password) {
      return res.status(400).json({ error: 'Please enter both owner email and password.' });
    }

    const trimmedEmail = email.trim().toLowerCase();

    // 1. Fetch owner from MongoDB
    const owner = await Owner.findOne({ email: trimmedEmail });
    if (!owner) {
      return res.status(404).json({
        error: "Access denied: No canteen owner account found for this email. Only authorized canteen owners can log in.",
      });
    }

    // 2. Role validation
    if (!owner.role || owner.role.toLowerCase() !== 'owner') {
      return res.status(403).json({
        error: "Access denied: This account does not have canteen owner privileges.",
      });
    }

    // 3. Strict canteen allocation validation
    if (!owner.canteenAssigned || !owner.canteenId || !owner.canteenId.trim()) {
      return res.status(403).json({
        error: "Access Denied: Your account is registered, but no canteen has been assigned to you yet. Please contact the campus administrator to assign your canteen.",
      });
    }

    // 4. Verify password
    if (owner.password && owner.password.trim()) {
      const isMatch = await bcrypt.compare(password, owner.password);
      if (!isMatch) {
        return res.status(401).json({ error: "Incorrect password. Please verify and try again." });
      }
    } else {
      // First-time owner initialization: hash and save the password
      owner.password = await bcrypt.hash(password, 10);
      await owner.save();
    }

    // 5. Generate secure 6-digit OTP
    const rawOtp = crypto.randomInt(100000, 1000000).toString();
    const otpHash = hashOtp(rawOtp);
    const expiresAt = new Date(Date.now() + 5 * 60 * 1000); // 5 minutes

    // 6. Save or update OtpVerification in MongoDB
    await OtpVerification.findOneAndUpdate(
      { email: trimmedEmail },
      {
        $set: {
          email: trimmedEmail,
          otpHash,
          expiresAt,
          attempts: 0,
          maxAttempts: 5,
          lastSentAt: new Date(),
          verified: false,
        },
      },
      { upsert: true, new: true }
    );

    // 7. Dispatch OTP through Gmail SMTP in background (non-blocking)
    console.log(`[OwnerAuth] Dispatching 6-digit OTP code to ${trimmedEmail} (Code: ${rawOtp})...`);
    sendOwnerOTP(trimmedEmail, rawOtp, owner.name || 'Canteen Owner').catch(err => {
      console.error('[OwnerAuth] Error sending owner OTP email in background:', err.message);
    });

    // 8. Return response without exposing OTP
    const ownerObj = owner.toObject ? owner.toObject() : { ...owner };
    delete ownerObj.password;
    res.json({
      success: true,
      message: 'OTP verification code sent to your registered email.',
      email: trimmedEmail,
      owner: ownerObj,
    });
  } catch (err) {
    console.error('[OwnerAuth] Login error:', err);
    res.status(500).json({ error: err.message || 'Owner authentication failed.' });
  }
});

// ── POST /api/owner/verify-otp (Verify 6-digit OTP -> Generates JWT) ─────────
router.post('/verify-otp', async (req, res) => {
  try {
    const { email, otp } = req.body;
    if (!email || !otp) {
      return res.status(400).json({ error: 'Email and 6-digit OTP code are required.' });
    }

    const trimmedEmail = email.trim().toLowerCase();
    const trimmedOtp = otp.toString().trim();

    if (trimmedOtp.length !== 6 || !/^\d+$/.test(trimmedOtp)) {
      return res.status(400).json({ error: 'Please enter a valid 6-digit OTP code.' });
    }

    // 1. Fetch OTP record
    const otpRecord = await OtpVerification.findOne({ email: trimmedEmail });
    if (!otpRecord) {
      return res.status(400).json({
        error: 'No active OTP verification session found. Please request a new code.',
      });
    }

    // 2. Check expiration
    if (new Date() > new Date(otpRecord.expiresAt)) {
      await OtpVerification.deleteOne({ _id: otpRecord._id });
      return res.status(400).json({
        error: 'This OTP has expired. Please tap Resend Code to receive a new one.',
      });
    }

    // 3. Check attempt limit
    if (otpRecord.attempts >= otpRecord.maxAttempts) {
      await OtpVerification.deleteOne({ _id: otpRecord._id });
      return res.status(429).json({
        error: 'Too many incorrect attempts. For security, please request a new OTP.',
      });
    }

    // 4. Validate OTP hash
    const inputHash = hashOtp(trimmedOtp);
    if (inputHash !== otpRecord.otpHash) {
      otpRecord.attempts += 1;
      await otpRecord.save();
      const remaining = otpRecord.maxAttempts - otpRecord.attempts;
      return res.status(400).json({
        error: `Incorrect verification code. ${remaining} attempt${remaining === 1 ? '' : 's'} remaining.`,
      });
    }

    // 5. Success: Invalidate OTP (single-use)
    await OtpVerification.deleteOne({ _id: otpRecord._id });

    // 6. Fetch owner and sign JWT
    const owner = await Owner.findOne({ email: trimmedEmail }).lean();
    if (!owner) {
      return res.status(404).json({ error: 'Owner record not found.' });
    }

    const token = jwt.sign(
      { uid: owner.uid, email: owner.email, role: 'owner', canteenId: owner.canteenId },
      JWT_SECRET,
      { expiresIn: '14d' }
    );

    delete owner.password;

    res.json({
      success: true,
      message: 'OTP verified successfully.',
      token,
      owner,
    });
  } catch (err) {
    console.error('[OwnerAuth] Verify OTP error:', err);
    res.status(500).json({ error: err.message || 'OTP verification failed.' });
  }
});

// ── POST /api/owner/resend-otp (Enforce cooldown -> Resend 6-digit OTP) ──────
router.post('/resend-otp', async (req, res) => {
  try {
    const { email } = req.body;
    if (!email || !email.trim()) {
      return res.status(400).json({ error: 'Email is required to resend OTP.' });
    }

    const trimmedEmail = email.trim().toLowerCase();

    // Verify owner exists
    const owner = await Owner.findOne({ email: trimmedEmail });
    if (!owner) {
      return res.status(404).json({ error: 'No canteen owner found with this email.' });
    }

    // Cooldown check (60 seconds)
    const existing = await OtpVerification.findOne({ email: trimmedEmail });
    if (existing && existing.lastSentAt) {
      const elapsed = Date.now() - new Date(existing.lastSentAt).getTime();
      const cooldownMs = 60 * 1000;
      if (elapsed < cooldownMs) {
        const remainingSec = Math.ceil((cooldownMs - elapsed) / 1000);
        return res.status(429).json({
          error: `Please wait ${remainingSec} seconds before requesting a new OTP.`,
        });
      }
    }

    // Generate new OTP
    const rawOtp = crypto.randomInt(100000, 1000000).toString();
    const otpHash = hashOtp(rawOtp);
    const expiresAt = new Date(Date.now() + 5 * 60 * 1000); // 5 minutes

    await OtpVerification.findOneAndUpdate(
      { email: trimmedEmail },
      {
        $set: {
          email: trimmedEmail,
          otpHash,
          expiresAt,
          attempts: 0,
          maxAttempts: 5,
          lastSentAt: new Date(),
          verified: false,
        },
      },
      { upsert: true, new: true }
    );

    // Send through Gmail SMTP in background (non-blocking)
    console.log(`[OwnerAuth] Resending OTP code to ${trimmedEmail} (Code: ${rawOtp})...`);
    sendOwnerOTP(trimmedEmail, rawOtp, owner.name || 'Canteen Owner').catch(err => {
      console.error('[OwnerAuth] Error resending owner OTP email in background:', err.message);
    });

    res.json({
      success: true,
      message: 'A fresh OTP code has been sent to your registered email.',
      email: trimmedEmail,
    });
  } catch (err) {
    console.error('[OwnerAuth] Resend OTP error:', err);
    res.status(500).json({ error: err.message || 'Failed to resend OTP.' });
  }
});

// ── Middleware: Verify Admin or Owner JWT Token ──────────────────────────────
function requireAdminAuth(req, res, next) {
  const authHeader = req.headers['authorization'] || '';
  const token = authHeader.startsWith('Bearer ')
    ? authHeader.slice(7).trim()
    : (req.headers['x-admin-token'] || req.query.token || '').trim();

  if (!token) {
    return res.status(401).json({ error: 'Unauthorized: Admin authentication token required. Please sign in.' });
  }

  try {
    const decoded = jwt.verify(token, JWT_SECRET);
    if (!decoded || (decoded.role !== 'admin' && decoded.role !== 'owner')) {
      return res.status(403).json({ error: 'Forbidden: Canteen owner or administrator privileges required.' });
    }
    req.admin = decoded;
    next();
  } catch (err) {
    return res.status(401).json({ error: 'Session expired or invalid token. Please sign in again.' });
  }
}

// ── POST /api/owners/admin/login (Admin/Owner Email & Password -> Sends 6-digit OTP) ─────
router.post('/admin/login', async (req, res) => {
  try {
    const { email, password } = req.body || {};
    if (!email || !password) {
      return res.status(400).json({ error: 'Please enter both your email and password.' });
    }

    const trimmedEmail = email.trim().toLowerCase();

    // 1. Check Owner collection first
    let account = await Owner.findOne({ email: trimmedEmail });
    let role = 'owner';

    // 2. If not found in Owner, check User collection for admin/owner role
    if (!account) {
      account = await User.findOne({ email: trimmedEmail, role: { $in: ['admin', 'owner'] } });
      if (account) role = account.role;
    }

    // 3. If account does not exist
    if (!account) {
      return res.status(404).json({
        error: 'Access denied: No canteen owner or administrator account found for this email.',
      });
    }

    // 4. Verify password
    if (account.password && account.password.trim()) {
      const isMatch = await bcrypt.compare(password, account.password);
      if (!isMatch) {
        return res.status(401).json({ error: 'Incorrect password. Please verify and try again.' });
      }
    } else {
      // First-time owner initialization: hash and save
      account.password = await bcrypt.hash(password, 10);
      await account.save();
    }

    // 5. Generate secure 6-digit numeric OTP
    const rawOtp = crypto.randomInt(100000, 1000000).toString();
    const otpHash = hashOtp(rawOtp);
    const expiresAt = new Date(Date.now() + 5 * 60 * 1000); // 5 minutes

    // 6. Save or update OtpVerification in MongoDB
    await OtpVerification.findOneAndUpdate(
      { email: trimmedEmail },
      {
        $set: {
          email: trimmedEmail,
          otpHash,
          expiresAt,
          attempts: 0,
          maxAttempts: 5,
          lastSentAt: new Date(),
          verified: false,
        },
      },
      { upsert: true, new: true }
    );

    // 7. Dispatch OTP through Gmail SMTP in background (non-blocking)
    console.log(`[AdminAuth] Dispatching 6-digit verification code to ${trimmedEmail} (Code: ${rawOtp})...`);
    sendAdminPortalOTP(trimmedEmail, rawOtp, account.name || 'Owner / Administrator').catch(err => {
      console.error('[AdminAuth] Error sending admin OTP email in background:', err.message);
    });

    res.json({
      success: true,
      message: 'A 6-digit verification code has been dispatched to your registered email.',
      email: trimmedEmail,
      name: account.name || 'Administrator',
      role,
    });
  } catch (err) {
    console.error('[AdminAuth] Login error:', err);
    res.status(500).json({ error: err.message || 'Authentication failed.' });
  }
});

// ── POST /api/owners/admin/verify-otp (Verify 6-digit OTP -> Generates Admin JWT) ─────────
router.post('/admin/verify-otp', async (req, res) => {
  try {
    const { email, otp } = req.body || {};
    if (!email || !otp) {
      return res.status(400).json({ error: 'Email and 6-digit verification code are required.' });
    }

    const trimmedEmail = email.trim().toLowerCase();
    const trimmedOtp = otp.toString().trim();

    if (trimmedOtp.length !== 6 || !/^\d+$/.test(trimmedOtp)) {
      return res.status(400).json({ error: 'Please enter a valid 6-digit verification code.' });
    }

    // 1. Fetch OTP record
    const otpRecord = await OtpVerification.findOne({ email: trimmedEmail });
    if (!otpRecord) {
      return res.status(400).json({
        error: 'No active OTP verification session found. Please request a new code.',
      });
    }

    // 2. Check expiration
    if (new Date() > new Date(otpRecord.expiresAt)) {
      await OtpVerification.deleteOne({ _id: otpRecord._id });
      return res.status(400).json({
        error: 'This verification code has expired. Please click Resend Code to receive a new one.',
      });
    }

    // 3. Check attempt limit
    if (otpRecord.attempts >= otpRecord.maxAttempts) {
      await OtpVerification.deleteOne({ _id: otpRecord._id });
      return res.status(429).json({
        error: 'Too many incorrect attempts. For security, please request a new verification code.',
      });
    }

    // 4. Validate OTP hash
    const inputHash = hashOtp(trimmedOtp);
    if (inputHash !== otpRecord.otpHash) {
      otpRecord.attempts += 1;
      await otpRecord.save();
      const remaining = otpRecord.maxAttempts - otpRecord.attempts;
      return res.status(400).json({
        error: `Incorrect verification code. ${remaining} attempt${remaining === 1 ? '' : 's'} remaining.`,
      });
    }

    // 5. Invalidate OTP (single-use)
    await OtpVerification.deleteOne({ _id: otpRecord._id });

    // 6. Look up account details
    let account = await Owner.findOne({ email: trimmedEmail }).lean();
    let role = 'owner';
    if (!account) {
      account = await User.findOne({ email: trimmedEmail }).lean();
      if (account) role = account.role;
    }

    if (!account) {
      return res.status(404).json({ error: 'User account record not found.' });
    }

    // 7. Generate JWT token
    const token = jwt.sign(
      {
        uid: account.uid,
        email: account.email,
        name: account.name || 'Admin',
        role: role || 'admin',
        canteenId: account.canteenId || '',
        isAdmin: true,
      },
      JWT_SECRET,
      { expiresIn: '14d' }
    );

    res.json({
      success: true,
      message: 'Admin authentication verified successfully.',
      token,
      admin: {
        uid: account.uid,
        email: account.email,
        name: account.name || 'Admin',
        role: role || 'admin',
        canteenId: account.canteenId || '',
      },
    });
  } catch (err) {
    console.error('[AdminAuth] Verify OTP error:', err);
    res.status(500).json({ error: err.message || 'Verification failed.' });
  }
});

// ── POST /api/owners/admin/resend-otp (Enforce cooldown -> Resend 6-digit OTP) ──────
router.post('/admin/resend-otp', async (req, res) => {
  try {
    const { email } = req.body || {};
    if (!email || !email.trim()) {
      return res.status(400).json({ error: 'Email is required to resend verification code.' });
    }

    const trimmedEmail = email.trim().toLowerCase();

    // Verify account exists
    let account = await Owner.findOne({ email: trimmedEmail });
    if (!account) {
      account = await User.findOne({ email: trimmedEmail, role: { $in: ['admin', 'owner'] } });
    }
    if (!account) {
      return res.status(404).json({ error: 'No authorized owner or admin account found with this email.' });
    }

    // Cooldown check (60 seconds)
    const existing = await OtpVerification.findOne({ email: trimmedEmail });
    if (existing && existing.lastSentAt) {
      const elapsed = Date.now() - new Date(existing.lastSentAt).getTime();
      const cooldownMs = 60 * 1000;
      if (elapsed < cooldownMs) {
        const remainingSec = Math.ceil((cooldownMs - elapsed) / 1000);
        return res.status(429).json({
          error: `Please wait ${remainingSec} seconds before requesting a new code.`,
        });
      }
    }

    // Generate new OTP
    const rawOtp = crypto.randomInt(100000, 1000000).toString();
    const otpHash = hashOtp(rawOtp);
    const expiresAt = new Date(Date.now() + 5 * 60 * 1000); // 5 minutes

    await OtpVerification.findOneAndUpdate(
      { email: trimmedEmail },
      {
        $set: {
          email: trimmedEmail,
          otpHash,
          expiresAt,
          attempts: 0,
          maxAttempts: 5,
          lastSentAt: new Date(),
          verified: false,
        },
      },
      { upsert: true, new: true }
    );

    console.log(`[AdminAuth] Resending verification code to ${trimmedEmail} (Code: ${rawOtp})...`);
    sendAdminPortalOTP(trimmedEmail, rawOtp, account.name || 'Owner / Administrator').catch(err => {
      console.error('[AdminAuth] Error resending admin OTP email in background:', err.message);
    });

    res.json({
      success: true,
      message: 'A fresh verification code has been dispatched to your registered email.',
      email: trimmedEmail,
    });
  } catch (err) {
    console.error('[AdminAuth] Resend OTP error:', err);
    res.status(500).json({ error: err.message || 'Failed to resend verification code.' });
  }
});

// ── GET /api/owners/admin/verify-token (Verify current session JWT token) ─────────
router.get('/admin/verify-token', requireAdminAuth, (req, res) => {
  res.json({
    success: true,
    message: 'Admin session is active and valid.',
    admin: req.admin,
  });
});

// ── GET /api/owners - List all owners (Requires Admin/Owner Auth) ────────────
router.get('/', requireAdminAuth, async (req, res) => {
  try {
    const owners = await Owner.find({}).sort({ createdAt: -1 }).select('-password').lean();
    res.json(owners);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// ── GET /api/owners/:uid - Get owner details by uid or email ─────────────────
router.get('/:uid', async (req, res) => {
  try {
    const query = req.params.uid.includes('@')
      ? { email: req.params.uid.trim().toLowerCase() }
      : { $or: [{ uid: req.params.uid }, { email: req.params.uid.trim().toLowerCase() }] };

    const owner = await Owner.findOne(query).select('-password').lean();
    if (!owner) return res.status(404).json({ error: 'Owner not found' });
    res.json(owner);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// ── POST /api/owners - Create or update owner profile (Requires Admin Auth) ──
router.post('/', requireAdminAuth, async (req, res) => {
  try {
    let { uid, name, email, password, role, canteenAssigned, canteenId, block, phone } = req.body;
    
    if (!email || !email.trim()) {
      return res.status(400).json({ error: 'Owner email is required.' });
    }

    const trimmedEmail = email.trim().toLowerCase();
    
    // Basic email format check
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(trimmedEmail)) {
      return res.status(400).json({ error: 'Please enter a valid email address format.' });
    }

    // Generate clean UID if not provided
    if (!uid || !uid.trim()) {
      uid = 'owner_' + Date.now() + '_' + crypto.randomBytes(3).toString('hex');
    }

    const updateDoc = {
      uid,
      name: (name || '').trim(),
      email: trimmedEmail,
      phone: (phone || '').trim(),
      role: role || 'owner',
      canteenAssigned: Boolean(canteenAssigned),
      canteenId: canteenAssigned ? (canteenId || '').trim() : '',
      block: canteenAssigned ? (block || '').trim() : '',
    };

    // If password provided, hash it with bcrypt
    if (password && password.trim()) {
      if (password.trim().length < 6) {
        return res.status(400).json({ error: 'Password must be at least 6 characters long.' });
      }
      updateDoc.password = await bcrypt.hash(password.trim(), 10);
    }

    // Check if updating existing owner or creating new
    const existing = await Owner.findOne({
      $or: [{ uid }, { email: trimmedEmail }]
    });

    let owner;
    if (existing) {
      // Don't overwrite existing password if no new password was submitted
      if (!updateDoc.password) {
        delete updateDoc.password;
      }
      owner = await Owner.findOneAndUpdate(
        { _id: existing._id },
        { $set: updateDoc },
        { new: true }
      ).select('-password');
    } else {
      if (!updateDoc.password) {
        return res.status(400).json({ error: 'A password of at least 6 characters is required when creating a new owner.' });
      }
      owner = await Owner.create(updateDoc);
      owner = owner.toObject();
      delete owner.password;
    }

    console.log(`[Admin Portal] Owner account saved: ${trimmedEmail} (Assigned: ${updateDoc.canteenAssigned})`);
    res.status(201).json(owner);
  } catch (err) {
    console.error('[Admin Portal] Save owner error:', err);
    res.status(400).json({ error: err.message });
  }
});

// ── PATCH /api/owners/:uid/assign - Assign or unassign a canteen ─────────────
router.patch('/:uid/assign', requireAdminAuth, async (req, res) => {
  try {
    const { canteenAssigned, canteenId, block } = req.body;
    const query = req.params.uid.includes('@')
      ? { email: req.params.uid.trim().toLowerCase() }
      : { uid: req.params.uid };

    const update = {
      canteenAssigned: Boolean(canteenAssigned),
      canteenId: canteenAssigned ? (canteenId || '').trim() : '',
      block: canteenAssigned ? (block || '').trim() : '',
    };

    const updated = await Owner.findOneAndUpdate(
      query,
      { $set: update },
      { new: true }
    ).select('-password');

    if (!updated) return res.status(404).json({ error: 'Owner not found' });
    console.log(`[Admin Portal] Owner ${updated.email} assignment updated: assigned=${updated.canteenAssigned}, canteen=${updated.canteenId}`);
    res.json(updated);
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

// ── PATCH /api/owners/:uid/password - Admin reset owner password ──────────────
router.patch('/:uid/password', requireAdminAuth, async (req, res) => {
  try {
    const { newPassword } = req.body;
    if (!newPassword || newPassword.trim().length < 6) {
      return res.status(400).json({ error: 'Password must be at least 6 characters long.' });
    }

    const query = req.params.uid.includes('@')
      ? { email: req.params.uid.trim().toLowerCase() }
      : { uid: req.params.uid };

    const hashedPassword = await bcrypt.hash(newPassword.trim(), 10);
    const updated = await Owner.findOneAndUpdate(
      query,
      { $set: { password: hashedPassword } },
      { new: true }
    ).select('-password');

    if (!updated) return res.status(404).json({ error: 'Owner not found' });
    console.log(`[Admin Portal] Owner ${updated.email} password successfully reset by admin.`);
    res.json({ success: true, message: `Password updated for ${updated.email}` });
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

// ── DELETE /api/owners/:uid - Remove owner account from MongoDB ───────────────
router.delete('/:uid', requireAdminAuth, async (req, res) => {
  try {
    const query = req.params.uid.includes('@')
      ? { email: req.params.uid.trim().toLowerCase() }
      : { uid: req.params.uid };

    const deleted = await Owner.findOneAndDelete(query);
    if (!deleted) return res.status(404).json({ error: 'Owner not found' });
    console.log(`[Admin Portal] Owner ${deleted.email} deleted from database.`);
    res.json({ success: true, message: `Owner ${deleted.email} successfully deleted.` });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

module.exports = router;
