const express = require('express');
const router = express.Router();
const crypto = require('crypto');
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const Admin = require('../models/Admin');
const Owner = require('../models/Owner');
const User = require('../models/User');
const Canteen = require('../models/Canteen');
const OtpVerification = require('../models/OtpVerification');
const Order = require('../models/Order');
const OrderVerification = require('../models/OrderVerification');
const { hashVerificationToken } = require('../services/qrVerificationService');
const { broadcastOrderStatus, broadcastQueueUpdate } = require('../services/socketService');
const { sendPushToUser } = require('../services/fcmService');
const { formatIstDateTime, formatIstTime } = require('../utils/timeHelper');
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

// ── Middleware: Verify Admin JWT Token (Strictly Admins Only) ─────────────────
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
    if (!decoded || decoded.role !== 'admin') {
      return res.status(403).json({
        error: 'Forbidden: Administrator privileges required. Canteen owners and students cannot access administrative endpoints.',
      });
    }
    req.admin = decoded;
    next();
  } catch (err) {
    return res.status(401).json({ error: 'Session expired or invalid token. Please sign in again.' });
  }
}

// ── Middleware: Verify Owner JWT Token & Authoritative Canteen Allocation ─────
async function requireOwnerAuth(req, res, next) {
  const authHeader = req.headers['authorization'] || '';
  const token = authHeader.startsWith('Bearer ')
    ? authHeader.slice(7).trim()
    : (req.headers['x-owner-token'] || req.query.token || '').trim();

  if (!token) {
    return res.status(401).json({
      success: false,
      code: 'UNAUTHORIZED',
      error: 'Unauthorized: Canteen owner authentication required. Please sign in.',
    });
  }

  try {
    const decoded = jwt.verify(token, JWT_SECRET);
    if (!decoded) {
      return res.status(401).json({
        success: false,
        code: 'UNAUTHORIZED',
        error: 'Invalid authentication session.',
      });
    }

    // Lookup owner record in MongoDB to verify active status and authoritative canteen assignment
    let owner = null;
    if (decoded.email) {
      owner = await Owner.findOne({ email: decoded.email.trim().toLowerCase() }).lean();
    }
    if (!owner && decoded.uid) {
      owner = await Owner.findOne({ uid: decoded.uid }).lean();
    }

    if (!owner) {
      return res.status(403).json({
        success: false,
        code: 'FORBIDDEN',
        error: 'Access denied: No active canteen owner account found for this session.',
      });
    }

    if (!owner.role || owner.role.toLowerCase() !== 'owner') {
      return res.status(403).json({
        success: false,
        code: 'FORBIDDEN',
        error: 'Access denied: This account does not possess canteen owner privileges.',
      });
    }

    const canteenId = (owner.canteenId || decoded.canteenId || '').trim();
    if (!owner.canteenAssigned || !canteenId) {
      return res.status(403).json({
        success: false,
        code: 'NO_CANTEEN_ASSIGNED',
        error: 'Access Denied: Your account is registered, but no canteen has been allocated to you yet. Please contact administration.',
      });
    }

    req.owner = {
      uid: owner.uid || decoded.uid,
      email: owner.email,
      name: owner.name,
      canteenId: canteenId,
    };
    next();
  } catch (err) {
    return res.status(401).json({
      success: false,
      code: 'SESSION_EXPIRED',
      error: 'Session expired or invalid authentication token. Please sign in again.',
    });
  }
}

// ── POST /api/owners/admin/register (Admin Registration -> Sends 6-digit OTP) ─
router.post('/admin/register', async (req, res) => {
  try {
    const { name, email, password, phone } = req.body || {};

    if (!name || !name.trim()) {
      return res.status(400).json({ error: 'Administrator full name is required.' });
    }
    if (!email || !email.trim()) {
      return res.status(400).json({ error: 'Administrator email is required.' });
    }
    if (!password || password.trim().length < 6) {
      return res.status(400).json({ error: 'Password must be at least 6 characters long.' });
    }

    const trimmedEmail = email.trim().toLowerCase();

    // Basic email format check
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(trimmedEmail)) {
      return res.status(400).json({ error: 'Please enter a valid email address format.' });
    }

    // 1. Check if an admin already exists with this email
    const existingAdmin = (await Admin.findOne({ email: trimmedEmail })) || (await User.findOne({ email: trimmedEmail, role: 'admin' }));
    if (existingAdmin) {
      return res.status(400).json({
        error: 'An administrator account with this email already exists. Please switch to the Sign In tab.',
      });
    }

    // 2. Check if registered as an Owner
    const existingOwner = await Owner.findOne({ email: trimmedEmail });
    if (existingOwner) {
      return res.status(400).json({
        error: 'This email is already registered as a Canteen Owner account. Please use an official administrator email address.',
      });
    }

    // 3. Hash password and generate 6-digit OTP
    const passwordHash = await bcrypt.hash(password.trim(), 10);
    const rawOtp = crypto.randomInt(100000, 1000000).toString();
    const otpHash = hashOtp(rawOtp);
    const expiresAt = new Date(Date.now() + 5 * 60 * 1000); // 5 minutes

    // 4. Save pending registration data and OTP in OtpVerification
    await OtpVerification.findOneAndUpdate(
      { email: trimmedEmail },
      {
        $set: {
          email: trimmedEmail,
          otpHash,
          tempData: {
            mode: 'register',
            name: name.trim(),
            phone: (phone || '').trim(),
            passwordHash,
          },
          expiresAt,
          attempts: 0,
          maxAttempts: 5,
          lastSentAt: new Date(),
          verified: false,
        },
      },
      { upsert: true, new: true }
    );

    // 5. Dispatch OTP via Gmail SMTP in background
    console.log(`[AdminAuth] Dispatching registration OTP to ${trimmedEmail} (Code: ${rawOtp})...`);
    sendAdminPortalOTP(trimmedEmail, rawOtp, name.trim()).catch(err => {
      console.error('[AdminAuth] Error sending admin registration OTP email:', err.message);
    });

    res.json({
      success: true,
      message: 'A 6-digit security code has been sent to your email to verify your administrator registration.',
      email: trimmedEmail,
      mode: 'register',
      ...(process.env.NODE_ENV !== 'production' ? { devOtp: rawOtp } : {})
    });
  } catch (err) {
    console.error('[AdminAuth] Registration error:', err);
    res.status(500).json({ error: err.message || 'Administrator registration failed.' });
  }
});

// ── POST /api/owners/admin/verify-register-otp (Verify OTP -> Creates Admin in MongoDB)
router.post('/admin/verify-register-otp', async (req, res) => {
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
    if (!otpRecord || !otpRecord.tempData || otpRecord.tempData.mode !== 'register') {
      return res.status(400).json({
        error: 'No active registration session found. Please register again.',
      });
    }

    // 2. Check expiration
    if (new Date() > new Date(otpRecord.expiresAt)) {
      await OtpVerification.deleteOne({ _id: otpRecord._id });
      return res.status(400).json({
        error: 'This verification code has expired. Please click Resend Code to receive a new one.',
      });
    }

    // 3. Check attempts
    if (otpRecord.attempts >= otpRecord.maxAttempts) {
      await OtpVerification.deleteOne({ _id: otpRecord._id });
      return res.status(429).json({
        error: 'Too many incorrect attempts. For security, please register again.',
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

    // 5. Invalidate OTP
    const { name, phone, passwordHash } = otpRecord.tempData;
    await OtpVerification.deleteOne({ _id: otpRecord._id });

    // 6. Create Admin document in MongoDB
    const uid = 'admin_' + Date.now() + '_' + crypto.randomBytes(3).toString('hex');
    const newAdmin = await Admin.create({
      uid,
      name,
      email: trimmedEmail,
      password: passwordHash,
      phone: phone || '',
      role: 'admin',
      isSuperAdmin: true,
    });

    // 7. Issue JWT token with role: 'admin'
    const token = jwt.sign(
      {
        uid: newAdmin.uid,
        email: newAdmin.email,
        name: newAdmin.name,
        role: 'admin',
        isAdmin: true,
      },
      JWT_SECRET,
      { expiresIn: '14d' }
    );

    console.log(`[AdminAuth] New Administrator registered and verified: ${newAdmin.email} (UID: ${newAdmin.uid})`);

    res.status(201).json({
      success: true,
      message: 'Administrator account registered and authenticated successfully!',
      token,
      admin: {
        uid: newAdmin.uid,
        email: newAdmin.email,
        name: newAdmin.name,
        role: 'admin',
      },
    });
  } catch (err) {
    console.error('[AdminAuth] Verify register OTP error:', err);
    res.status(500).json({ error: err.message || 'Admin verification failed.' });
  }
});

// ── POST /api/owners/admin/login (Admin Sign In -> Sends 6-digit OTP; Rejects Owners/Users)
router.post('/admin/login', async (req, res) => {
  try {
    const { email, password } = req.body || {};
    if (!email || !password) {
      return res.status(400).json({ error: 'Please enter both your administrator email and password.' });
    }

    const trimmedEmail = email.trim().toLowerCase();

    // 1. Strictly look for Admin account
    let adminAccount = await Admin.findOne({ email: trimmedEmail });
    if (!adminAccount) {
      adminAccount = await User.findOne({ email: trimmedEmail, role: 'admin' });
    }

    // 2. If not an admin, check if owner or student to give an explicit rejection message
    if (!adminAccount) {
      const isOwner = await Owner.findOne({ email: trimmedEmail });
      if (isOwner) {
        return res.status(403).json({
          error: 'Access Denied: You are registered as a Canteen Owner. Only registered administrators can log into the Admin Portal. Please log into the Quickbite Mobile App.',
        });
      }

      const isStudent = await User.findOne({ email: trimmedEmail });
      if (isStudent) {
        return res.status(403).json({
          error: 'Access Denied: Student accounts cannot access the Admin Portal. Please log into the Quickbite Mobile App.',
        });
      }

      return res.status(404).json({
        error: 'No administrator account found with this email. If you are a new admin, click "Register" beside Sign In to create your account.',
      });
    }

    // 3. Verify bcrypt password
    const isMatch = await bcrypt.compare(password, adminAccount.password);
    if (!isMatch) {
      return res.status(401).json({ error: 'Incorrect password. Please verify and try again.' });
    }

    // 4. Generate secure 6-digit numeric OTP
    const rawOtp = crypto.randomInt(100000, 1000000).toString();
    const otpHash = hashOtp(rawOtp);
    const expiresAt = new Date(Date.now() + 5 * 60 * 1000); // 5 minutes

    // 5. Save or update OtpVerification in MongoDB
    await OtpVerification.findOneAndUpdate(
      { email: trimmedEmail },
      {
        $set: {
          email: trimmedEmail,
          otpHash,
          tempData: { mode: 'login' },
          expiresAt,
          attempts: 0,
          maxAttempts: 5,
          lastSentAt: new Date(),
          verified: false,
        },
      },
      { upsert: true, new: true }
    );

    // 6. Dispatch OTP through Gmail SMTP in background
    console.log(`[AdminAuth] Dispatching 6-digit login verification code to ${trimmedEmail} (Code: ${rawOtp})...`);
    sendAdminPortalOTP(trimmedEmail, rawOtp, adminAccount.name || 'Administrator').catch(err => {
      console.error('[AdminAuth] Error sending admin login OTP email in background:', err.message);
    });

    res.json({
      success: true,
      message: 'A 6-digit verification code has been dispatched to your registered administrator email.',
      email: trimmedEmail,
      name: adminAccount.name || 'Administrator',
      role: 'admin',
      mode: 'login',
      ...(process.env.NODE_ENV !== 'production' ? { devOtp: rawOtp } : {})
    });
  } catch (err) {
    console.error('[AdminAuth] Login error:', err);
    res.status(500).json({ error: err.message || 'Authentication failed.' });
  }
});

// ── POST /api/owners/admin/verify-otp (Verify 6-digit Login OTP -> Issues JWT Token) ────
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
        error: 'Too many incorrect attempts. For security, please sign in again.',
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

    // 6. Look up Admin account
    let adminAccount = await Admin.findOne({ email: trimmedEmail }).lean();
    if (!adminAccount) {
      adminAccount = await User.findOne({ email: trimmedEmail, role: 'admin' }).lean();
    }

    if (!adminAccount) {
      return res.status(403).json({
        error: 'Access Denied: Only registered administrators have access to this portal.',
      });
    }

    // 7. Generate JWT token with role: 'admin'
    const token = jwt.sign(
      {
        uid: adminAccount.uid,
        email: adminAccount.email,
        name: adminAccount.name || 'Administrator',
        role: 'admin',
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
        uid: adminAccount.uid,
        email: adminAccount.email,
        name: adminAccount.name || 'Administrator',
        role: 'admin',
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

    // 1. Fetch OTP session
    const existing = await OtpVerification.findOne({ email: trimmedEmail });
    if (!existing) {
      return res.status(404).json({ error: 'No active session found. Please sign in or register.' });
    }

    // 2. Cooldown check (60 seconds)
    if (existing.lastSentAt) {
      const elapsed = Date.now() - new Date(existing.lastSentAt).getTime();
      const cooldownMs = 60 * 1000;
      if (elapsed < cooldownMs) {
        const remainingSec = Math.ceil((cooldownMs - elapsed) / 1000);
        return res.status(429).json({
          error: `Please wait ${remainingSec} seconds before requesting a new code.`,
        });
      }
    }

    // 3. Generate new OTP
    const rawOtp = crypto.randomInt(100000, 1000000).toString();
    const otpHash = hashOtp(rawOtp);
    const expiresAt = new Date(Date.now() + 5 * 60 * 1000); // 5 minutes

    await OtpVerification.findOneAndUpdate(
      { email: trimmedEmail },
      {
        $set: {
          otpHash,
          expiresAt,
          attempts: 0,
          maxAttempts: 5,
          lastSentAt: new Date(),
          verified: false,
        },
      },
      { new: true }
    );

    const displayName = existing.tempData?.name || 'Administrator';
    console.log(`[AdminAuth] Resending verification code to ${trimmedEmail} (Code: ${rawOtp})...`);
    sendAdminPortalOTP(trimmedEmail, rawOtp, displayName).catch(err => {
      console.error('[AdminAuth] Error resending admin OTP email in background:', err.message);
    });

    res.json({
      success: true,
      message: 'A fresh verification code has been dispatched to your registered email.',
      email: trimmedEmail,
      ...(process.env.NODE_ENV !== 'production' ? { devOtp: rawOtp } : {})
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

// ── POST /api/owner/orders/verify-qr - Dynamic QR Code Order Pickup Verification ─────
router.post('/orders/verify-qr', requireOwnerAuth, async (req, res) => {
  try {
    const { token } = req.body || {};
    if (!token || typeof token !== 'string' || !token.trim()) {
      return res.status(400).json({
        success: false,
        code: 'INVALID_INPUT',
        error: 'Verification QR token is required.',
      });
    }

    const cleanToken = token.trim();
    const tokenHash = hashVerificationToken(cleanToken);

    // 1. Locate verification record by hashed token or fallback directly by Order ID / Token Number
    const verification = await OrderVerification.findOne({ tokenHash });
    let order = null;

    if (!verification) {
      order = await findOrderByTokenOrId(cleanToken);
      if (!order) {
        return res.status(404).json({
          success: false,
          code: 'INVALID_QR',
          error: 'Invalid QR code. Verification record or order ID not found.',
        });
      }

      // Check canteen allocation
      if (order.canteenId && req.owner.canteenId && order.canteenId !== req.owner.canteenId) {
        const orderCanteen = await Canteen.findOne({ id: order.canteenId }).select('name').lean();
        const orderCanteenName = orderCanteen?.name || order.canteenId;
        return res.status(403).json({
          success: false,
          code: 'WRONG_CANTEEN',
          error: `Wrong canteen: This order belongs to ${orderCanteenName}. You are only authorized to verify orders for your assigned canteen.`,
        });
      }

      if (order.status === 'CANCELLED') {
        return res.status(400).json({
          success: false,
          code: 'ORDER_CANCELLED',
          error: 'This order has been cancelled and cannot be picked up.',
        });
      }

      if (order.status === 'PICKED_UP' || order.status === 'COMPLETED') {
        return res.status(400).json({
          success: false,
          code: 'ALREADY_REDEEMED',
          error: 'This order has already been marked as picked up.',
        });
      }

      // Mark as PICKED_UP
      const now = new Date();
      const nowLocal = formatIstDateTime(now);
      order.status = 'PICKED_UP';
      order.pickedUpAt = now;
      order.pickedUpAtLocal = nowLocal;
      order.completedAt = now;
      order.completedAtLocal = nowLocal;
      order.verifiedByOwnerId = req.owner.uid;
      if (!order.statusHistory) order.statusHistory = [];
      order.statusHistory.push({
        status: 'PICKED_UP',
        timestamp: now,
        timestampLocal: nowLocal,
      });
      await order.save();

      try {
        broadcastOrderStatus(order);
        broadcastQueueUpdate(order.canteenId);
        if (order.studentId && order.studentId !== 'student_guest') {
          sendPushToUser(order.studentId, {
            title: `Order #${order.tokenNumber || order.orderId} Picked Up!`,
            body: 'Your order has been verified and picked up. Enjoy your meal!',
            data: { orderId: order.orderId, type: 'ORDER_PICKED_UP', status: 'PICKED_UP' },
          }).catch(() => {});
        }
      } catch (realtimeErr) {
        console.warn('[OwnerVerification] Direct QR realtime broadcast warning:', realtimeErr.message);
      }

      return res.status(200).json({
        success: true,
        code: 'VERIFIED_SUCCESS',
        message: 'Order verified and redeemed successfully via Order ID QR.',
        order: {
          orderId: order.orderId,
          tokenNumber: order.tokenNumber,
          studentName: order.studentName,
          studentPhone: order.studentPhone,
          canteenId: order.canteenId,
          pickupCanteenName: order.pickupCanteenName,
          pickupCounter: order.pickupCounter,
          totalAmount: order.totalAmount,
          items: order.items,
          status: 'PICKED_UP',
          pickedUpAt: now,
          pickedUpAtLocal: nowLocal,
        },
        verification: {
          verifiedAt: now,
          verifiedBy: req.owner.uid,
        },
      });
    }

    // 2. Check if already used/redeemed
    if (verification.status === 'REDEEMED') {
      return res.status(400).json({
        success: false,
        code: 'ALREADY_REDEEMED',
        error: `This QR code has already been redeemed${verification.usedAt ? ` at ${formatIstTime(verification.usedAt)}` : ''}.`,
      });
    }

    // 3. Check if verification was cancelled
    if (verification.status === 'CANCELLED') {
      return res.status(400).json({
        success: false,
        code: 'ORDER_CANCELLED',
        error: 'Cancelled order. This order has been cancelled.',
      });
    }

    // 4. Validate expiration (explicit date validation, never relying solely on TTL)
    const now = new Date();
    if (now > new Date(verification.expiresAt) || verification.status === 'EXPIRED') {
      if (verification.status === 'ACTIVE') {
        await OrderVerification.updateOne({ _id: verification._id }, { $set: { status: 'EXPIRED' } }).catch(() => {});
      }
      return res.status(400).json({
        success: false,
        code: 'EXPIRED_QR',
        error: 'This QR code has expired. Please ask the customer to refresh their order screen to get a new code.',
      });
    }

    // 5. Verify canteen allocation strictly from authenticated owner account
    if (verification.canteenId !== req.owner.canteenId) {
      const orderCanteen = await Canteen.findOne({ id: verification.canteenId }).select('name').lean();
      const orderCanteenName = orderCanteen?.name || verification.canteenId;
      return res.status(403).json({
        success: false,
        code: 'WRONG_CANTEEN',
        error: `Wrong canteen: This order belongs to ${orderCanteenName}. You are only authorized to verify orders for your assigned canteen.`,
      });
    }

    // 6. Fetch associated order
    const order = await Order.findOne({ orderId: verification.orderId });
    if (!order) {
      return res.status(404).json({
        success: false,
        code: 'ORDER_NOT_FOUND',
        error: 'Associated order was not found in database.',
      });
    }

    // 7. Validate Order Status: Must be in READY state
    if (order.status === 'CANCELLED') {
      return res.status(400).json({
        success: false,
        code: 'ORDER_CANCELLED',
        error: 'This order has been cancelled and cannot be picked up.',
      });
    }

    if (order.status === 'PICKED_UP' || order.status === 'COMPLETED') {
      return res.status(400).json({
        success: false,
        code: 'ALREADY_REDEEMED',
        error: 'This order has already been marked as picked up.',
      });
    }

    if (order.status !== 'READY') {
      return res.status(400).json({
        success: false,
        code: 'ORDER_NOT_READY',
        error: `Order not ready. Current status is "${order.status}". Orders must be in READY state before pickup verification.`,
        currentStatus: order.status,
      });
    }

    // 8. Atomic conditional redemption (prevents race condition / simultaneous scans)
    const redeemedVerification = await OrderVerification.findOneAndUpdate(
      {
        _id: verification._id,
        status: 'ACTIVE',
        expiresAt: { $gt: now },
      },
      {
        $set: {
          status: 'REDEEMED',
          usedAt: now,
          verifiedBy: req.owner.uid,
        },
      },
      { new: true }
    );

    if (!redeemedVerification) {
      return res.status(409).json({
        success: false,
        code: 'CONCURRENT_REDEEM',
        error: 'Concurrent redemption detected. This QR code was just redeemed by another device or request.',
      });
    }

    // 9. Update Order status to PICKED_UP with server-authoritative timestamps
    const nowLocal = formatIstDateTime(now);
    order.status = 'PICKED_UP';
    order.pickedUpAt = now;
    order.pickedUpAtLocal = nowLocal;
    order.completedAt = now;
    order.completedAtLocal = nowLocal;
    order.verifiedByOwnerId = req.owner.uid;
    if (!order.statusHistory) order.statusHistory = [];
    order.statusHistory.push({
      status: 'PICKED_UP',
      timestamp: now,
      timestampLocal: nowLocal,
    });
    await order.save();

    // 10. Real-time updates & notifications
    try {
      broadcastOrderStatus(order);
      broadcastQueueUpdate(order.canteenId);
      if (order.studentId && order.studentId !== 'student_guest') {
        sendPushToUser(order.studentId, {
          title: `Order #${order.tokenNumber || order.orderId} Picked Up!`,
          body: 'Your order has been verified and picked up. Enjoy your meal!',
          data: { orderId: order.orderId, type: 'ORDER_PICKED_UP', status: 'PICKED_UP' },
        }).catch(() => {});
      }
    } catch (realtimeErr) {
      console.warn('[OwnerVerification] Realtime broadcast warning:', realtimeErr.message);
    }

    // 11. Return structured success response
    return res.status(200).json({
      success: true,
      code: 'VERIFIED_SUCCESS',
      message: 'Order verified and redeemed successfully.',
      order: {
        orderId: order.orderId,
        tokenNumber: order.tokenNumber,
        studentName: order.studentName,
        studentPhone: order.studentPhone,
        canteenId: order.canteenId,
        pickupCanteenName: order.pickupCanteenName,
        pickupCounter: order.pickupCounter,
        totalAmount: order.totalAmount,
        items: order.items,
        status: 'PICKED_UP',
        pickedUpAt: now,
        pickedUpAtLocal: nowLocal,
      },
      verification: {
        verifiedAt: now,
        verifiedBy: req.owner.uid,
      },
    });
  } catch (err) {
    console.error('[OwnerVerification] Unexpected error:', err);
    return res.status(500).json({
      success: false,
      code: 'SERVER_ERROR',
      error: 'An unexpected internal error occurred during order verification.',
    });
  }
});

/**
 * Helper to find an order by verification token (Code 128 barcode or QR token hash or orderId)
 */
async function findOrderByTokenOrId(token) {
  if (!token || typeof token !== 'string') return null;
  const clean = token.trim();

  // 1. Direct match on verificationToken
  let order = await Order.findOne({ verificationToken: clean });
  if (order) return order;

  // 2. Check OrderVerification by SHA-256 hash (backward compatibility for QR tokens)
  const tokenHash = hashVerificationToken(clean);
  const verification = await OrderVerification.findOne({ tokenHash });
  if (verification) {
    order = await Order.findOne({ orderId: verification.orderId });
    if (order) return order;
  }

  // 3. Fallback match by orderId
  order = await Order.findOne({ orderId: clean });
  if (order) return order;

  // 4. Fallback match by token number candidates
  const rawNum = clean.replace(/^[^0-9]+/g, '').trim();
  if (rawNum) {
    order = await Order.findOne({ tokenNumber: { $in: [clean, rawNum, `#${rawNum}`, `#Q${rawNum}`, `Q${rawNum}`] } }).sort({ createdAt: -1 });
  }
  return order;
}

// ── POST /api/owner/orders/verify-barcode - Step 1: Secure Barcode Inspection & Validation ──
router.post('/orders/verify-barcode', requireOwnerAuth, async (req, res) => {
  try {
    const { token } = req.body || {};
    if (!token || typeof token !== 'string' || !token.trim()) {
      return res.status(400).json({
        success: false,
        code: 'INVALID_BARCODE',
        error: 'This barcode is invalid. Please scan a valid Quick Bite order barcode.',
      });
    }

    const cleanToken = token.trim();
    const order = await findOrderByTokenOrId(cleanToken);

    // Condition 1 & 2: Does the barcode exist in the database and belong to an actual order?
    if (!order) {
      return res.status(404).json({
        success: false,
        code: 'INVALID_BARCODE',
        error: 'This barcode is invalid. Please scan a valid Quick Bite order barcode.',
      });
    }

    // Condition 3: Is the order associated with the currently authenticated canteen owner?
    if (order.canteenId !== req.owner.canteenId) {
      return res.status(403).json({
        success: false,
        code: 'WRONG_OWNER',
        error: 'This order does not belong to your canteen.',
        orderCanteenId: order.canteenId,
        ownerCanteenId: req.owner.canteenId,
      });
    }

    // Condition 4: Has the order already been collected?
    if (order.status === 'PICKED_UP' || order.status === 'COMPLETED' || order.barcodeStatus === 'USED') {
      return res.status(400).json({
        success: false,
        code: 'ALREADY_COLLECTED',
        error: 'This order has already been collected.',
        collectedAt: order.collectedAt || order.pickedUpAt,
        collectedAtLocal: order.pickedUpAtLocal || order.completedAtLocal,
      });
    }

    // Condition 5: Is the barcode valid, active, and not cancelled/expired?
    if (order.status === 'CANCELLED' || order.barcodeStatus === 'CANCELLED') {
      return res.status(400).json({
        success: false,
        code: 'ORDER_CANCELLED',
        error: 'This order has been cancelled.',
      });
    }

    const now = new Date();
    if (order.expiresAt && now > new Date(order.expiresAt)) {
      return res.status(400).json({
        success: false,
        code: 'EXPIRED_BARCODE',
        error: 'This barcode has expired. Please contact the canteen owner.',
      });
    }

    // Condition 6: Is the order eligible for collection? (Must be in READY / Ready for Pickup state)
    if (order.status !== 'READY') {
      return res.status(400).json({
        success: false,
        code: 'ORDER_NOT_READY',
        error: 'This order is not ready for collection yet.',
        currentStatus: order.status,
      });
    }

    // All conditions verified successfully! Return Green Success structured payload
    const orderDate = order.orderDate || formatIstDate(order.orderPlacedAt || order.createdAt);
    const orderTime = order.orderTime || formatIstTime(order.orderPlacedAt || order.createdAt);

    return res.status(200).json({
      success: true,
      code: 'VERIFIED_SUCCESS',
      message: 'ORDER VERIFIED SUCCESSFULLY',
      order: {
        orderId: order.orderId,
        tokenNumber: order.tokenNumber,
        studentName: order.studentName || 'Student',
        studentPhone: order.studentPhone || '',
        items: (order.items || []).map(it => ({
          itemId: it.itemId,
          name: it.name,
          quantity: it.quantity,
          price: it.price,
          selectedOption: it.selectedOption || null,
        })),
        totalAmount: order.totalAmount,
        createdAt: order.createdAt,
        orderDate: orderDate,
        orderTime: orderTime,
        orderPlacedAtLocal: order.orderPlacedAtLocal || `${orderDate}, ${orderTime}`,
        status: order.status,
        canteenId: order.canteenId,
        pickupCounter: order.pickupCounter || 'Counter 1',
        verificationToken: order.verificationToken || cleanToken,
      },
    });
  } catch (err) {
    console.error('[OwnerBarcodeVerify] Unexpected error:', err);
    return res.status(500).json({
      success: false,
      code: 'SERVER_ERROR',
      error: 'Unable to verify the order. Please check your internet connection and try again.',
    });
  }
});

// ── POST /api/owner/orders/confirm-pickup - Step 2: Atomic Pickup Confirmation ──────────────
router.post('/orders/confirm-pickup', requireOwnerAuth, async (req, res) => {
  try {
    const { token, orderId } = req.body || {};
    const lookupKey = (token || orderId || '').trim();

    if (!lookupKey) {
      return res.status(400).json({
        success: false,
        code: 'INVALID_BARCODE',
        error: 'This barcode is invalid. Please scan a valid Quick Bite order barcode.',
      });
    }

    // 1. Initial lookup to verify ownership & existing state
    const existingOrder = await findOrderByTokenOrId(lookupKey);
    if (!existingOrder) {
      return res.status(404).json({
        success: false,
        code: 'INVALID_BARCODE',
        error: 'This barcode is invalid. Please scan a valid Quick Bite order barcode.',
      });
    }

    if (existingOrder.canteenId !== req.owner.canteenId) {
      return res.status(403).json({
        success: false,
        code: 'WRONG_OWNER',
        error: 'This order does not belong to your canteen.',
      });
    }

    if (existingOrder.status === 'PICKED_UP' || existingOrder.status === 'COMPLETED' || existingOrder.barcodeStatus === 'USED') {
      return res.status(400).json({
        success: false,
        code: 'ALREADY_COLLECTED',
        error: 'This order has already been collected.',
      });
    }

    if (existingOrder.status !== 'READY') {
      return res.status(400).json({
        success: false,
        code: 'ORDER_NOT_READY',
        error: 'This order is not ready for collection yet.',
        currentStatus: existingOrder.status,
      });
    }

    // 2. Atomic MongoDB update (strict condition: status MUST be READY, barcodeStatus != USED)
    // Prevents two simultaneous scans from confirming the same order (race condition safeguard)
    const now = new Date();
    const nowLocal = formatIstDateTime(now);

    const updatedOrder = await Order.findOneAndUpdate(
      {
        orderId: existingOrder.orderId,
        canteenId: req.owner.canteenId,
        status: 'READY',
        barcodeStatus: { $ne: 'USED' },
      },
      {
        $set: {
          status: 'PICKED_UP',
          barcodeStatus: 'USED',
          usedAt: now,
          collectedAt: now,
          collectedBy: req.owner.uid,
          pickedUpAt: now,
          pickedUpAtLocal: nowLocal,
          completedAt: now,
          completedAtLocal: nowLocal,
          verifiedByOwnerId: req.owner.uid,
        },
        $push: {
          statusHistory: {
            status: 'PICKED_UP',
            timestamp: now,
            timestampLocal: nowLocal,
          },
        },
      },
      { new: true }
    );

    if (!updatedOrder) {
      // If atomic update matched 0 documents, it was just confirmed by another concurrent request
      return res.status(409).json({
        success: false,
        code: 'ALREADY_COLLECTED',
        error: 'This order has already been collected.',
      });
    }

    // Mark OrderVerification as REDEEMED if exists
    try {
      await OrderVerification.updateMany(
        { orderId: updatedOrder.orderId, status: 'ACTIVE' },
        {
          $set: {
            status: 'REDEEMED',
            usedAt: now,
            verifiedBy: req.owner.uid,
          },
        }
      );
    } catch (vErr) {
      console.warn('[ConfirmPickup] OrderVerification sync warning:', vErr.message);
    }

    // 3. Real-time WebSocket Broadcast & Push Notification
    try {
      broadcastOrderStatus(updatedOrder);
      broadcastQueueUpdate(updatedOrder.canteenId);
      if (updatedOrder.studentId && updatedOrder.studentId !== 'student_guest') {
        sendPushToUser(updatedOrder.studentId, {
          title: `Order #${updatedOrder.tokenNumber || updatedOrder.orderId} Collected!`,
          body: 'Your order has been verified and successfully collected. Enjoy your meal!',
          data: { orderId: updatedOrder.orderId, type: 'ORDER_COLLECTED', status: 'PICKED_UP' },
        }).catch(() => {});
      }
    } catch (rtErr) {
      console.warn('[ConfirmPickup] Realtime broadcast warning:', rtErr.message);
    }

    return res.status(200).json({
      success: true,
      code: 'COLLECTED_SUCCESS',
      message: 'Order Successfully Collected',
      order: {
        orderId: updatedOrder.orderId,
        tokenNumber: updatedOrder.tokenNumber,
        studentName: updatedOrder.studentName,
        status: updatedOrder.status,
        totalAmount: updatedOrder.totalAmount,
        collectedAt: now,
        collectedAtLocal: nowLocal,
        collectedBy: req.owner.uid,
      },
    });
  } catch (err) {
    console.error('[ConfirmPickup] Unexpected error:', err);
    return res.status(500).json({
      success: false,
      code: 'SERVER_ERROR',
      error: 'Unable to verify the order. Please check your internet connection and try again.',
    });
  }
});

module.exports = router;
