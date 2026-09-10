const express = require('express');
const router = express.Router();
const crypto = require('crypto');
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const User = require('../models/User');
const Owner = require('../models/Owner');
const PasswordResetToken = require('../models/PasswordResetToken');
const { sendPasswordResetEmail } = require('../services/emailService');

const JWT_SECRET = process.env.JWT_SECRET || 'quickbite-super-secure-jwt-secret-key-2026';

// Helper: Hash token with SHA-256
function hashToken(token) {
  return crypto.createHash('sha256').update(token.trim()).digest('hex');
}

// ── POST /api/auth/register ──────────────────────────────────────────────────
router.post('/register', async (req, res) => {
  try {
    const {
      name,
      email,
      password,
      registrationNumber,
      department,
      avatarId,
      phone,
      course,
      role = 'student',
    } = req.body;

    if (!email || !email.trim() || !password) {
      return res.status(400).json({ error: 'Email and password are required.' });
    }

    const trimmedEmail = email.trim().toLowerCase();
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(trimmedEmail)) {
      return res.status(400).json({ error: 'Please enter a valid email address.' });
    }

    if (password.length < 6) {
      return res.status(400).json({ error: 'Password must be at least 6 characters long.' });
    }

    const existingUser = await User.findOne({ email: trimmedEmail });
    if (existingUser) {
      return res.status(409).json({
        success: false,
        error: 'User already registered. Please log in instead.',
        message: 'User already registered. Please log in instead.',
      });
    }

    const hashedPassword = await bcrypt.hash(password, 10);
    const uid = `student_${Date.now()}`;

    const newUser = await User.create({
      uid,
      name: name?.trim() || trimmedEmail.split('@')[0],
      email: trimmedEmail,
      password: hashedPassword,
      role,
      registrationNumber: registrationNumber?.trim() || '',
      department: department?.trim() || '',
      avatarId: avatarId || 'scholar',
      phone: phone?.trim() || '',
      course: course?.trim() || '',
    });

    const token = jwt.sign(
      { uid: newUser.uid, email: newUser.email, role: newUser.role },
      JWT_SECRET,
      { expiresIn: '30d' }
    );

    const userObj = newUser.toObject();
    delete userObj.password;

    res.status(201).json({
      success: true,
      message: 'Registration successful',
      token,
      user: userObj,
    });
  } catch (err) {
    console.error('Registration error:', err);
    if (err.code === 11000) {
      return res.status(409).json({
        success: false,
        error: 'User already registered. Please log in instead.',
        message: 'User already registered. Please log in instead.',
      });
    }
    res.status(500).json({ error: err.message || 'Registration failed.' });
  }
});

// ── POST /api/auth/login ─────────────────────────────────────────────────────
router.post('/login', async (req, res) => {
  try {
    const { email, password } = req.body;
    if (!email || !password) {
      return res.status(400).json({ error: 'Email and password are required.' });
    }

    const trimmedEmail = email.trim().toLowerCase();
    const user = await User.findOne({ email: trimmedEmail });
    if (!user) {
      return res.status(404).json({ error: 'Account not found. Please check your details or register a new account.' });
    }

    if (!user.password) {
      return res.status(401).json({ error: 'No password set for this account. Please use Forgot Password to create one.' });
    }

    const isMatch = await bcrypt.compare(password, user.password);
    if (!isMatch) {
      return res.status(401).json({ error: 'Incorrect password. Please verify and try again.' });
    }

    const token = jwt.sign(
      { uid: user.uid, email: user.email, role: user.role || 'student' },
      JWT_SECRET,
      { expiresIn: '30d' }
    );

    const userObj = user.toObject();
    delete userObj.password;

    res.json({
      success: true,
      token,
      user: userObj,
    });
  } catch (err) {
    console.error('Login error:', err);
    res.status(500).json({ error: err.message || 'Login failed.' });
  }
});

// ── POST /api/auth/forgot-password ───────────────────────────────────────────
router.post('/forgot-password', async (req, res) => {
  try {
    const { email } = req.body;
    if (!email || !email.trim()) {
      return res.status(400).json({ error: 'Please enter your registered email address.' });
    }

    const trimmedEmail = email.trim().toLowerCase();

    // Verify user or owner exists in database
    const user = await User.findOne({ email: trimmedEmail });
    const owner = !user ? await Owner.findOne({ email: trimmedEmail }) : null;

    if (!user && !owner) {
      return res.status(404).json({
        error: 'No account found with this email. Please check the email or register a new account.',
      });
    }

    const targetName = user ? user.name : (owner ? owner.name : 'User');

    // Generate secure 32-byte crypto token
    const rawToken = crypto.randomBytes(32).toString('hex');
    const tokenHash = hashToken(rawToken);
    const expiresAt = new Date(Date.now() + 20 * 60 * 1000); // 20 minutes

    // Invalidate any existing unused reset tokens for this email
    await PasswordResetToken.deleteMany({ email: trimmedEmail });

    // Store new token
    await PasswordResetToken.create({
      email: trimmedEmail,
      tokenHash,
      expiresAt,
      used: false,
    });

    // Formulate reset URL
    let resetUrl;
    if (process.env.PASSWORD_RESET_URL && process.env.PASSWORD_RESET_URL.trim()) {
      const baseUrl = process.env.PASSWORD_RESET_URL.trim();
      resetUrl = `${baseUrl}?token=${rawToken}&email=${encodeURIComponent(trimmedEmail)}`;
    } else {
      const protocol = req.protocol;
      const host = req.get('host');
      resetUrl = `${protocol}://${host}/api/auth/reset-password?token=${rawToken}&email=${encodeURIComponent(trimmedEmail)}`;
    }

    // Send email using Nodemailer Gmail SMTP in background (non-blocking)
    console.log(`[Auth] Dispatching password reset email to ${trimmedEmail}...`);
    sendPasswordResetEmail(trimmedEmail, resetUrl, targetName).catch(err => {
      console.error('[Auth] Error sending reset email in background:', err.message);
    });

    console.log(`[Auth Reset URL Generated]: ${resetUrl}`);

    res.json({
      success: true,
      message: 'Password reset link sent! Please check your email inbox.',
      email: trimmedEmail,
      token: rawToken,
      resetUrl,
    });
  } catch (err) {
    console.error('Forgot password error:', err);
    res.status(500).json({ error: err.message || 'Failed to process password reset request.' });
  }
});

// ── POST /api/auth/reset-password ───────────────────────────────────────────
router.post('/reset-password', async (req, res) => {
  try {
    const { token, email, newPassword } = req.body;

    if (!token || !email || !newPassword) {
      return res.status(400).json({ error: 'Token, email, and new password are required.' });
    }

    if (newPassword.length < 6) {
      return res.status(400).json({ error: 'Password must be at least 6 characters long.' });
    }

    const trimmedEmail = email.trim().toLowerCase();
    const tokenHash = hashToken(token);

    // Validate token against MongoDB
    const resetRecord = await PasswordResetToken.findOne({
      email: trimmedEmail,
      tokenHash,
      used: false,
      expiresAt: { $gt: new Date() },
    });

    if (!resetRecord) {
      return res.status(400).json({
        error: 'Your password reset link is invalid or has expired. Please request a new link.',
      });
    }

    // Hash the new password with bcrypt
    const hashedPassword = await bcrypt.hash(newPassword, 10);

    // Update User (and Owner if applicable)
    let updated = false;
    const userUpdate = await User.findOneAndUpdate(
      { email: trimmedEmail },
      { $set: { password: hashedPassword } },
      { new: true }
    );
    if (userUpdate) updated = true;

    const ownerUpdate = await Owner.findOneAndUpdate(
      { email: trimmedEmail },
      { $set: { password: hashedPassword } },
      { new: true }
    );
    if (ownerUpdate) updated = true;

    if (!updated) {
      return res.status(404).json({ error: 'Account not found for password update.' });
    }

    // Mark token as used (single-use)
    resetRecord.used = true;
    await resetRecord.save();

    res.json({
      success: true,
      message: 'Password updated successfully. Please continue to sign in with your new credentials.',
    });
  } catch (err) {
    console.error('Reset password error:', err);
    res.status(500).json({ error: err.message || 'Failed to update password.' });
  }
});

// ── GET /api/auth/reset-password (Instant App Redirector & Deep Link Launcher)
router.get('/reset-password', async (req, res) => {
  const { token, email } = req.query;

  const rawToken = token || '';
  const rawEmail = email || '';
  const customSchemeUrl = `quickbite://reset-password?token=${encodeURIComponent(rawToken)}&email=${encodeURIComponent(rawEmail)}`;
  const secondarySchemeUrl = `messq://reset-password?token=${encodeURIComponent(rawToken)}&email=${encodeURIComponent(rawEmail)}`;
  const intentUrl = `intent://reset-password?token=${encodeURIComponent(rawToken)}&email=${encodeURIComponent(rawEmail)}#Intent;scheme=quickbite;package=com.aistudio.campuscanteen.fxdz;end`;

  const html = `
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Quick Bite - Opening App...</title>
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&display=swap" rel="stylesheet">
  <style>
    :root {
      --primary: #FF6B00;
      --primary-hover: #E65100;
      --bg: #0F172A;
      --card-bg: #1E293B;
      --text: #F8FAFC;
      --text-muted: #94A3B8;
      --border: #334155;
    }
    * { box-sizing: border-box; margin: 0; padding: 0; font-family: 'Plus Jakarta Sans', sans-serif; }
    body {
      background: var(--bg);
      color: var(--text);
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 24px 16px;
      text-align: center;
    }
    .card {
      width: 100%;
      max-width: 440px;
      background: var(--card-bg);
      border: 1px solid var(--border);
      border-radius: 28px;
      padding: 44px 28px;
      box-shadow: 0 24px 48px rgba(0, 0, 0, 0.45);
    }
    .logo-container {
      width: 80px;
      height: 80px;
      margin: 0 auto 24px;
      background: linear-gradient(135deg, #FF6B00 0%, #E65100 100%);
      border-radius: 22px;
      display: flex;
      align-items: center;
      justify-content: center;
      box-shadow: 0 12px 28px rgba(255, 107, 0, 0.4);
      animation: pulse 2s infinite ease-in-out;
    }
    .logo-icon {
      font-size: 38px;
    }
    @keyframes pulse {
      0%, 100% { transform: scale(1); }
      50% { transform: scale(1.06); }
    }
    .badge {
      display: inline-block;
      background: rgba(255, 107, 0, 0.15);
      color: var(--primary);
      padding: 6px 16px;
      border-radius: 20px;
      font-size: 11px;
      font-weight: 700;
      letter-spacing: 1.2px;
      text-transform: uppercase;
      margin-bottom: 16px;
    }
    h1 {
      font-size: 24px;
      font-weight: 800;
      margin-bottom: 12px;
      letter-spacing: -0.5px;
    }
    p.desc {
      font-size: 14.5px;
      color: var(--text-muted);
      line-height: 22px;
      margin-bottom: 32px;
    }
    .spinner {
      width: 32px;
      height: 32px;
      border: 3.5px solid rgba(255, 255, 255, 0.1);
      border-top-color: var(--primary);
      border-radius: 50%;
      animation: spin 0.8s linear infinite;
      margin: 0 auto 24px;
    }
    @keyframes spin {
      to { transform: rotate(360deg); }
    }
    .btn {
      display: block;
      width: 100%;
      background: var(--primary);
      color: #FFFFFF !important;
      text-decoration: none;
      border: none;
      border-radius: 16px;
      padding: 16px;
      font-size: 15px;
      font-weight: 700;
      cursor: pointer;
      box-shadow: 0 8px 20px rgba(255, 107, 0, 0.35);
      transition: transform 0.15s, background 0.2s;
    }
    .btn:active {
      transform: scale(0.98);
    }
    .btn:hover {
      background: var(--primary-hover);
    }
    .hint {
      margin-top: 18px;
      font-size: 12px;
      color: #64748B;
      line-height: 18px;
    }
  </style>
</head>
<body>
  <div class="card">
    <div class="logo-container">
      <span class="logo-icon">🍔</span>
    </div>
    <div class="badge">Password Reset</div>
    <h1>Opening Quick Bite...</h1>
    <p class="desc">Redirecting you to the app so you can securely set your new password.</p>

    <div class="spinner"></div>

    <a id="openAppBtn" href="${customSchemeUrl}" class="btn">
      📱 Open in Quick Bite App
    </a>

    <p class="hint">
      If the app didn't open automatically, tap the button above.
    </p>
  </div>

  <script>
    const primaryUrl = "${customSchemeUrl}";
    const intentUrl = "${intentUrl}";
    const secondaryUrl = "${secondarySchemeUrl}";

    function launchApp() {
      window.location.replace(primaryUrl);
      setTimeout(function() {
        window.location.href = intentUrl;
      }, 400);
      setTimeout(function() {
        window.location.href = secondaryUrl;
      }, 1000);
    }

    launchApp();

    document.getElementById('openAppBtn').addEventListener('click', function(e) {
      launchApp();
    });
  </script>
</body>
</html>
  `.trim();

  res.send(html);
});

module.exports = router;
