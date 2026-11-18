// Owner & Admin Portal Routes: 2-factor authentication, administrative management, and counter pickup verification
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
const { formatIstDateTime, formatIstTime, formatIstDate } = require('../utils/timeHelper');
const { sendOwnerOTP, sendAdminPortalOTP } = require('../services/emailService');

const JWT_SECRET = process.env.JWT_SECRET || 'quickbite-super-secure-jwt-secret-key-2026';

// Helper: Hash OTP with SHA-256
function hashOtp(otp) {
  return crypto.createHash('sha256').update(otp.trim()).digest('hex');
}

// Owner Login: Authenticates owner credentials and sends 6-digit OTP for 2FA
router.post('/login', async (req, res) => {
  try {
    const { email, password } = req.body;
    if (!email || !password) {
      return res.status(400).json({ error: 'Please enter both owner email and password.' });
    }

    const trimmedEmail = email.trim().toLowerCase();

    const owner = await Owner.findOne({ email: trimmedEmail });
    if (!owner) {
      return res.status(404).json({
        error: "Access denied: No canteen owner account found for this email. Only authorized canteen owners can log in.",
      });
    }

    if (!owner.role || owner.role.toLowerCase() !== 'owner') {
      return res.status(403).json({
        error: "Access denied: This account does not have canteen owner privileges.",
      });
    }

    if (!owner.canteenAssigned || !owner.canteenId || !owner.canteenId.trim()) {
      return res.status(403).json({
        error: "Access Denied: Your account is registered, but no canteen has been assigned to you yet. Please contact the campus administrator to assign your canteen.",
      });
    }

    if (owner.password && owner.password.trim()) {
      const isMatch = await bcrypt.compare(password, owner.password);
      if (!isMatch) {
        return res.status(401).json({ error: "Incorrect password. Please verify and try again." });
      }
    } else {
      owner.password = await bcrypt.hash(password, 10);
      await owner.save();
    }

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

    console.log(`[OwnerAuth] Dispatching 6-digit OTP code to ${trimmedEmail} (Code: ${rawOtp})...`);
    sendOwnerOTP(trimmedEmail, rawOtp, owner.name || 'Canteen Owner').catch(err => {
      console.error('[OwnerAuth] Error sending owner OTP email in background:', err.message);
    });

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

// Owner OTP Verification: Validates OTP against stored hash and issues signed JWT session token
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

    const otpRecord = await OtpVerification.findOne({ email: trimmedEmail });
    if (!otpRecord) {
      return res.status(400).json({
        error: 'No active OTP verification session found. Please request a new code.',
      });
    }

    if (new Date() > new Date(otpRecord.expiresAt)) {
      await OtpVerification.deleteOne({ _id: otpRecord._id });
      return res.status(400).json({
        error: 'This OTP has expired. Please tap Resend Code to receive a new one.',
      });
    }

    if (otpRecord.attempts >= otpRecord.maxAttempts) {
      await OtpVerification.deleteOne({ _id: otpRecord._id });
      return res.status(429).json({
        error: 'Too many incorrect attempts. For security, please request a new OTP.',
      });
    }

    const inputHash = hashOtp(trimmedOtp);
    if (inputHash !== otpRecord.otpHash) {
      otpRecord.attempts += 1;
      await otpRecord.save();
      const remaining = otpRecord.maxAttempts - otpRecord.attempts;
      return res.status(400).json({
        error: `Incorrect verification code. ${remaining} attempt${remaining === 1 ? '' : 's'} remaining.`,
      });
    }

    await OtpVerification.deleteOne({ _id: otpRecord._id });

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

// Resends 6-digit OTP enforcing a cooldown interval
router.post('/resend-otp', async (req, res) => {
  try {
    const { email } = req.body;
    if (!email || !email.trim()) {
      return res.status(400).json({ error: 'Email is required to resend OTP.' });
    }

    const trimmedEmail = email.trim().toLowerCase();

    const owner = await Owner.findOne({ email: trimmedEmail });
    if (!owner) {
      return res.status(404).json({ error: 'No canteen owner found with this email.' });
    }

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

// Middleware: Verifies administrator JWT token for privileged management endpoints
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

// Middleware: Verifies canteen owner JWT token and attaches authorized canteenId to request
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

    if (decoded.role === 'admin') {
      let admin = null;
      if (decoded.email) {
        admin = await Admin.findOne({ email: decoded.email.trim().toLowerCase() }).lean();
      }
      if (!admin && decoded.uid) {
        admin = await Admin.findOne({ uid: decoded.uid }).lean();
      }
      if (admin) {
        req.owner = {
          uid: admin.uid || decoded.uid,
          email: admin.email,
          name: admin.name || 'Campus Administrator',
          role: 'admin',
          canteenId: '', // Campus-wide access
        };
        return next();
      }
    }

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

// Admin Registration: Registers new administrator and initiates 2FA email verification
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

    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(trimmedEmail)) {
      return res.status(400).json({ error: 'Please enter a valid email address format.' });
    }

    const existingAdmin = (await Admin.findOne({ email: trimmedEmail })) || (await User.findOne({ email: trimmedEmail, role: 'admin' }));
    if (existingAdmin) {
      return res.status(400).json({
        error: 'An administrator account with this email already exists. Please switch to the Sign In tab.',
      });
    }

    const existingOwner = await Owner.findOne({ email: trimmedEmail });
    if (existingOwner) {
      return res.status(400).json({
        error: 'This email is already registered as a Canteen Owner account. Please use an official administrator email address.',
      });
    }

    const passwordHash = await bcrypt.hash(password.trim(), 10);
    const rawOtp = crypto.randomInt(100000, 1000000).toString();
    const otpHash = hashOtp(rawOtp);
    const expiresAt = new Date(Date.now() + 5 * 60 * 1000); // 5 minutes

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

// Admin Registration Verification: Completes administrator account creation upon valid OTP
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

    const otpRecord = await OtpVerification.findOne({ email: trimmedEmail });
    if (!otpRecord || !otpRecord.tempData || otpRecord.tempData.mode !== 'register') {
      return res.status(400).json({
        error: 'No active registration session found. Please register again.',
      });
    }

    if (new Date() > new Date(otpRecord.expiresAt)) {
      await OtpVerification.deleteOne({ _id: otpRecord._id });
      return res.status(400).json({
        error: 'This verification code has expired. Please click Resend Code to receive a new one.',
      });
    }

    if (otpRecord.attempts >= otpRecord.maxAttempts) {
      await OtpVerification.deleteOne({ _id: otpRecord._id });
      return res.status(429).json({
        error: 'Too many incorrect attempts. For security, please register again.',
      });
    }

    const inputHash = hashOtp(trimmedOtp);
    if (inputHash !== otpRecord.otpHash) {
      otpRecord.attempts += 1;
      await otpRecord.save();
      const remaining = otpRecord.maxAttempts - otpRecord.attempts;
      return res.status(400).json({
        error: `Incorrect verification code. ${remaining} attempt${remaining === 1 ? '' : 's'} remaining.`,
      });
    }

    const { name, phone, passwordHash } = otpRecord.tempData;
    await OtpVerification.deleteOne({ _id: otpRecord._id });

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

// Admin Sign In: Authenticates administrator credentials and dispatches 2FA OTP
router.post('/admin/login', async (req, res) => {
  try {
    const { email, password } = req.body || {};
    if (!email || !password) {
      return res.status(400).json({ error: 'Please enter both your administrator email and password.' });
    }

    const trimmedEmail = email.trim().toLowerCase();

    let adminAccount = await Admin.findOne({ email: trimmedEmail });
    if (!adminAccount) {
      adminAccount = await User.findOne({ email: trimmedEmail, role: 'admin' });
    }

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

    const isMatch = await bcrypt.compare(password, adminAccount.password);
    if (!isMatch) {
      return res.status(401).json({ error: 'Incorrect password. Please verify and try again.' });
    }

    const rawOtp = crypto.randomInt(100000, 1000000).toString();
    const otpHash = hashOtp(rawOtp);
    const expiresAt = new Date(Date.now() + 5 * 60 * 1000); // 5 minutes

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

// Admin OTP Verification: Validates 2FA OTP and issues administrator JWT session token
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

    const otpRecord = await OtpVerification.findOne({ email: trimmedEmail });
    if (!otpRecord) {
      return res.status(400).json({
        error: 'No active OTP verification session found. Please request a new code.',
      });
    }

    if (new Date() > new Date(otpRecord.expiresAt)) {
      await OtpVerification.deleteOne({ _id: otpRecord._id });
      return res.status(400).json({
        error: 'This verification code has expired. Please click Resend Code to receive a new one.',
      });
    }

    if (otpRecord.attempts >= otpRecord.maxAttempts) {
      await OtpVerification.deleteOne({ _id: otpRecord._id });
      return res.status(429).json({
        error: 'Too many incorrect attempts. For security, please sign in again.',
      });
    }

    const inputHash = hashOtp(trimmedOtp);
    if (inputHash !== otpRecord.otpHash) {
      otpRecord.attempts += 1;
      await otpRecord.save();
      const remaining = otpRecord.maxAttempts - otpRecord.attempts;
      return res.status(400).json({
        error: `Incorrect verification code. ${remaining} attempt${remaining === 1 ? '' : 's'} remaining.`,
      });
    }

    await OtpVerification.deleteOne({ _id: otpRecord._id });

    let adminAccount = await Admin.findOne({ email: trimmedEmail }).lean();
    if (!adminAccount) {
      adminAccount = await User.findOne({ email: trimmedEmail, role: 'admin' }).lean();
    }

    if (!adminAccount) {
      return res.status(403).json({
        error: 'Access Denied: Only registered administrators have access to this portal.',
      });
    }

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

// Resends administrator 2FA OTP with cooldown enforcement
router.post('/admin/resend-otp', async (req, res) => {
  try {
    const { email } = req.body || {};
    if (!email || !email.trim()) {
      return res.status(400).json({ error: 'Email is required to resend verification code.' });
    }

    const trimmedEmail = email.trim().toLowerCase();

    const existing = await OtpVerification.findOne({ email: trimmedEmail });
    if (!existing) {
      return res.status(404).json({ error: 'No active session found. Please sign in or register.' });
    }

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

router.get('/admin/verify-token', requireAdminAuth, (req, res) => {
  res.json({
    success: true,
    message: 'Admin session is active and valid.',
    admin: req.admin,
  });
});

// Owner Accounts: CRUD operations for managing canteen owner profiles
router.get('/', requireAdminAuth, async (req, res) => {
  try {
    const owners = await Owner.find({}).sort({ createdAt: -1 }).select('-password').lean();
    res.json(owners);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

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

router.post('/', requireAdminAuth, async (req, res) => {
  try {
    let { uid, name, email, password, role, canteenAssigned, canteenId, block, phone } = req.body;
    
    if (!email || !email.trim()) {
      return res.status(400).json({ error: 'Owner email is required.' });
    }

    const trimmedEmail = email.trim().toLowerCase();
    
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(trimmedEmail)) {
      return res.status(400).json({ error: 'Please enter a valid email address format.' });
    }

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

    if (password && password.trim()) {
      if (password.trim().length < 6) {
        return res.status(400).json({ error: 'Password must be at least 6 characters long.' });
      }
      updateDoc.password = await bcrypt.hash(password.trim(), 10);
    }

    const existing = await Owner.findOne({
      $or: [{ uid }, { email: trimmedEmail }]
    });

    let owner;
    if (existing) {
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

// QR Code Verification: Validates dynamic QR token presented at pickup counter
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

    const verification = await OrderVerification.findOne({ tokenHash });
    let order = null;

    if (verification) {
      order = await Order.findOne({ orderId: verification.orderId });
    }
    if (!order) {
      order = await findOrderByTokenOrId(cleanToken);
    }

    if (!order) {
      return res.status(404).json({
        success: false,
        code: 'INVALID_QR',
        error: 'Invalid QR code. Verification record or order ID not found.',
      });
    }

    if (order.canteenId && req.owner.canteenId && order.canteenId !== req.owner.canteenId) {
      const orderCanteen = await Canteen.findOne({ id: order.canteenId }).select('name').lean();
      const orderCanteenName = orderCanteen?.name || order.canteenId;
      return res.status(403).json({
        success: false,
        code: 'WRONG_CANTEEN',
        error: `Wrong canteen: This order belongs to ${orderCanteenName}. You are only authorized to verify orders for your assigned canteen.`,
      });
    }

    const now = new Date();
    if ((verification && (now > new Date(verification.expiresAt) || verification.status === 'EXPIRED')) ||
        (order.expiresAt && now > new Date(order.expiresAt))) {
      return res.status(400).json({
        success: false,
        code: 'EXPIRED_QR',
        error: 'This QR code has expired. Please ask the customer to refresh their order screen to get a new code.',
      });
    }

    let studentRollNumber = order.studentRollNumber || '';
    if (!studentRollNumber && order.studentId) {
      const studentUser = await User.findOne({
        $or: [
          { uid: order.studentId },
          { registrationNumber: order.studentId },
          { email: order.studentId.toLowerCase() },
        ],
      }).select('registrationNumber').lean();
      if (studentUser && studentUser.registrationNumber) {
        studentRollNumber = studentUser.registrationNumber;
      }
    }

    const category = getOrderStatusCategory(order.status, order.barcodeStatus);
    const statusMessage = STATUS_MESSAGES[category] || STATUS_MESSAGES.PREPARING;
    const eligibleForPickup = (category === 'READY');

    const orderDate = order.orderDate || formatIstDate(order.orderPlacedAt || order.createdAt);
    const orderTime = order.orderTime || formatIstTime(order.orderPlacedAt || order.createdAt);

    return res.status(200).json({
      success: true,
      code: category,
      message: statusMessage,
      statusMessage: statusMessage,
      eligibleForPickup: eligibleForPickup,
      currentStatus: order.status,
      collectedAt: order.collectedAt || order.pickedUpAt || null,
      collectedAtLocal: order.pickedUpAtLocal || order.completedAtLocal || '',
      collectedBy: order.collectedBy || order.verifiedByOwnerId || null,
      order: {
        orderId: order.orderId,
        tokenNumber: order.tokenNumber,
        studentName: order.studentName || 'Student',
        studentRollNumber: studentRollNumber,
        studentPhone: order.studentPhone || '',
        studentCourse: order.studentCourse || '',
        canteenId: order.canteenId,
        pickupCanteenName: order.pickupCanteenName,
        pickupCounter: order.pickupCounter || 'Counter 1',
        totalAmount: order.totalAmount,
        items: (order.items || []).map(it => ({
          itemId: it.itemId,
          name: it.name,
          quantity: it.quantity,
          price: it.price,
          subtotal: it.subtotal || (it.price * it.quantity),
          prepMinutes: it.prepMinutes || 7,
          selectedOption: it.selectedOption || null,
        })),
        status: order.status,
        orderDate: orderDate,
        orderTime: orderTime,
        orderPlacedAtLocal: order.orderPlacedAtLocal || `${orderDate}, ${orderTime}`,
        verificationToken: order.verificationToken || cleanToken,
        statusHistory: order.statusHistory || [],
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
function escapeRegex(str) {
  return str.replace(/[/\-\^$*+?.()|[\]{}]/g, '\\$&');
}

/**
 * Robust Helper to find an order by verification token:
 * - Case-insensitive Code 128 barcode token (with or without 'QB-' prefix)
 * - Raw token from QR codes or parsed JSON QR payloads
 * - OrderVerification tokenHash
 * - OrderId (case-insensitive)
 * - MongoDB ObjectId
 * - TokenNumber with prefix variations (#, Q, #Q)
 */
async function findOrderByTokenOrId(token) {
  if (!token || typeof token !== 'string') return null;
  const clean = token.trim();
  if (!clean) return null;

  // 1. If scanned input is a JSON string (e.g. from QR scanner)
  if (clean.startsWith('{') && clean.endsWith('}')) {
    try {
      const parsed = JSON.parse(clean);
      if (parsed.token) {
        const found = await findOrderByTokenOrId(parsed.token);
        if (found) return found;
      }
      if (parsed.orderId) {
        const found = await findOrderByTokenOrId(parsed.orderId);
        if (found) return found;
      }
      if (parsed.verificationToken) {
        const found = await findOrderByTokenOrId(parsed.verificationToken);
        if (found) return found;
      }
    } catch (_) {}
  }

  // 2. Direct case-insensitive match on verificationToken
  let order = await Order.findOne({
    verificationToken: { $regex: new RegExp(`^${escapeRegex(clean)}$`, 'i') },
  });
  if (order) return order;

  // 2b. Try with / without "QB-" prefix
  const upper = clean.toUpperCase();
  const withPrefix = upper.startsWith('QB-') ? upper : `QB-${upper}`;
  const withoutPrefix = upper.startsWith('QB-') ? upper.slice(3) : upper;
  order = await Order.findOne({
    verificationToken: { $in: [withPrefix, withoutPrefix] },
  });
  if (order) return order;

  // 3. Match OrderVerification tokenHash
  try {
    const tokenHash = hashVerificationToken(clean);
    const verification = await OrderVerification.findOne({ tokenHash });
    if (verification) {
      order = await Order.findOne({ orderId: verification.orderId });
      if (order) return order;
    }
  } catch (_) {}

  // 4. Case-insensitive match on orderId
  order = await Order.findOne({
    orderId: { $regex: new RegExp(`^${escapeRegex(clean)}$`, 'i') },
  });
  if (order) return order;

  // 5. MongoDB ObjectId match
  const mongoose = require('mongoose');
  if (mongoose.Types.ObjectId.isValid(clean)) {
    order = await Order.findById(clean);
    if (order) return order;
  }

  // 6. Token number match (e.g. "101", "#101", "Q101")
  const rawNum = clean.replace(/^[^0-9]+/g, '').trim();
  if (rawNum) {
    order = await Order.findOne({
      tokenNumber: { $in: [clean, rawNum, `#${rawNum}`, `#Q${rawNum}`, `Q${rawNum}`] },
    }).sort({ createdAt: -1 });
    if (order) return order;
  }

  return null;
}

const STATUS_MESSAGES = {
  PENDING: 'Order is pending owner confirmation.',
  ORDER_PLACED: 'Order has been placed and is waiting for canteen confirmation.',
  ACCEPTED: 'Order has been accepted. Kitchen preparation will begin shortly.',
  PREPARING: 'Order is currently being prepared. Not ready for pickup yet.',
  READY: 'Order is ready for pickup! Hand over food to student.',
  COLLECTED: 'This order has already been collected.',
  CANCELLED: 'This order has been cancelled and cannot be collected.',
  REJECTED: 'This order was rejected and cannot be collected.',
};

function getOrderStatusCategory(status, barcodeStatus) {
  const normStatus = (status || '').toUpperCase().trim();
  const normBarcodeStatus = (barcodeStatus || '').toUpperCase().trim();

  if (normStatus === 'CANCELLED' || normBarcodeStatus === 'CANCELLED') {
    return 'CANCELLED';
  }
  if (normStatus === 'REJECTED') {
    return 'REJECTED';
  }
  if (normStatus === 'COMPLETED' || normStatus === 'PICKED_UP' || normStatus === 'COLLECTED' || normBarcodeStatus === 'USED') {
    return 'COLLECTED';
  }
  if (normStatus === 'READY') {
    return 'READY';
  }
  if (normStatus === 'PREPARING') {
    return 'PREPARING';
  }
  if (normStatus === 'ACCEPTED' || normStatus === 'CONFIRMED') {
    return 'ACCEPTED';
  }
  if (['NEW', 'WAITING', 'PLACED', 'ORDER_PLACED', 'PENDING'].includes(normStatus)) {
    return 'PENDING';
  }
  return 'PREPARING';
}

// Barcode Verification: Validates 1D Code 128 barcode at pickup counter and verifies order status
router.post('/orders/verify-barcode', requireOwnerAuth, async (req, res) => {
  try {
    const { token } = req.body || {};
    if (!token || typeof token !== 'string' || !token.trim()) {
      return res.status(400).json({
        success: false,
        code: 'INVALID_BARCODE',
        error: 'Please scan or enter a valid QuickBite order barcode.',
      });
    }

    const cleanToken = token.trim();
    const order = await findOrderByTokenOrId(cleanToken);

    if (!order) {
      return res.status(404).json({
        success: false,
        code: 'INVALID_BARCODE',
        error: 'No order found matching this barcode. Please scan a valid QuickBite order barcode.',
      });
    }

    // Canteen authorization check
    if (req.owner.role !== 'admin' && order.canteenId !== req.owner.canteenId) {
      const orderCanteen = await Canteen.findOne({ id: order.canteenId }).lean();
      const orderCanteenName = orderCanteen?.name || order.pickupCanteenName || order.canteenId;
      return res.status(403).json({
        success: false,
        code: 'WRONG_OWNER',
        error: `This order belongs to ${orderCanteenName}, not your canteen.`,
        orderCanteenId: order.canteenId,
        ownerCanteenId: req.owner.canteenId,
      });
    }

    const now = new Date();
    if (order.expiresAt && now > new Date(order.expiresAt)) {
      return res.status(400).json({
        success: false,
        code: 'EXPIRED_BARCODE',
        error: 'This pickup barcode has expired. Please contact support.',
      });
    }

    let studentRollNumber = order.studentRollNumber || '';
    if (!studentRollNumber && order.studentId) {
      const studentUser = await User.findOne({
        $or: [
          { uid: order.studentId },
          { registrationNumber: order.studentId },
          { email: order.studentId.toLowerCase() },
        ],
      }).select('registrationNumber').lean();
      if (studentUser && studentUser.registrationNumber) {
        studentRollNumber = studentUser.registrationNumber;
      }
    }

    const category = getOrderStatusCategory(order.status, order.barcodeStatus);
    const statusMessage = STATUS_MESSAGES[category] || STATUS_MESSAGES.PREPARING;
    const eligibleForPickup = (category === 'READY');

    const orderDate = order.orderDate || formatIstDate(order.orderPlacedAt || order.createdAt);
    const orderTime = order.orderTime || formatIstTime(order.orderPlacedAt || order.createdAt);

    return res.status(200).json({
      success: true,
      code: category,
      message: statusMessage,
      statusMessage: statusMessage,
      eligibleForPickup: eligibleForPickup,
      currentStatus: order.status,
      collectedAt: order.collectedAt || order.pickedUpAt || null,
      collectedAtLocal: order.pickedUpAtLocal || order.completedAtLocal || '',
      collectedBy: order.collectedBy || order.verifiedByOwnerId || null,
      order: {
        orderId: order.orderId,
        tokenNumber: order.tokenNumber,
        studentName: order.studentName || 'Student',
        studentRollNumber: studentRollNumber,
        studentPhone: order.studentPhone || '',
        studentCourse: order.studentCourse || '',
        items: (order.items || []).map(it => ({
          itemId: it.itemId,
          name: it.name,
          quantity: it.quantity,
          price: it.price,
          subtotal: it.subtotal || (it.price * it.quantity),
          prepMinutes: it.prepMinutes || 7,
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
        statusHistory: order.statusHistory || [],
      },
    });
  } catch (err) {
    console.error('[OwnerBarcodeVerify] Unexpected error:', err);
    return res.status(500).json({
      success: false,
      code: 'SERVER_ERROR',
      error: 'Unable to verify order due to a server error. Please try again.',
    });
  }
});

// Atomic Pickup Confirmation: Atomically transitions order to PICKED_UP status and broadcasts WebSocket update
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

    const existingOrder = await findOrderByTokenOrId(lookupKey);
    if (!existingOrder) {
      return res.status(404).json({
        success: false,
        code: 'INVALID_BARCODE',
        error: 'This barcode is invalid. Please scan a valid Quick Bite order barcode.',
      });
    }

    if (req.owner.role !== 'admin' && existingOrder.canteenId !== req.owner.canteenId) {
      return res.status(403).json({
        success: false,
        code: 'WRONG_OWNER',
        error: 'This order does not belong to your canteen.',
      });
    }

    if (existingOrder.status === 'CANCELLED' || existingOrder.barcodeStatus === 'CANCELLED') {
      return res.status(400).json({
        success: false,
        code: 'ORDER_CANCELLED',
        error: STATUS_MESSAGES.CANCELLED,
      });
    }

    if (existingOrder.status === 'PICKED_UP' || existingOrder.status === 'COMPLETED' || existingOrder.status === 'COLLECTED' || existingOrder.barcodeStatus === 'USED') {
      return res.status(400).json({
        success: false,
        code: 'ALREADY_COLLECTED',
        error: STATUS_MESSAGES.COLLECTED,
      });
    }

    if (existingOrder.status !== 'READY') {
      const cat = getOrderStatusCategory(existingOrder.status, existingOrder.barcodeStatus);
      return res.status(400).json({
        success: false,
        code: cat,
        error: STATUS_MESSAGES[cat] || 'Order is not ready for pickup.',
        currentStatus: existingOrder.status,
      });
    }

    const now = new Date();
    const nowLocal = formatIstDateTime(now);
    const prevStatus = existingOrder.status;

    const updatedOrder = await Order.findOneAndUpdate(
      {
        orderId: existingOrder.orderId,
        canteenId: req.owner.canteenId,
        status: 'READY',
        barcodeStatus: { $ne: 'USED' },
      },
      {
        $set: {
          status: 'COMPLETED',
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
            status: 'COLLECTED',
            previousStatus: prevStatus,
            timestamp: now,
            timestampLocal: nowLocal,
            changedBy: req.owner.uid,
            changedByRole: 'OWNER',
          },
        },
      },
      { new: true }
    );

    if (!updatedOrder) {
      return res.status(409).json({
        success: false,
        code: 'ALREADY_COLLECTED',
        error: STATUS_MESSAGES.COLLECTED,
      });
    }

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

    try {
      broadcastOrderStatus(updatedOrder);
      broadcastQueueUpdate(updatedOrder.canteenId);
      if (updatedOrder.studentId && updatedOrder.studentId !== 'student_guest') {
        sendPushToUser(updatedOrder.studentId, {
          title: `Order #${updatedOrder.tokenNumber || updatedOrder.orderId} Collected!`,
          body: 'Your order has been verified and successfully collected. Enjoy your meal!',
          data: { orderId: updatedOrder.orderId, type: 'ORDER_COLLECTED', status: 'COMPLETED' },
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

router.requireOwnerAuth = requireOwnerAuth;
module.exports = router;
