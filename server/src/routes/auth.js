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

// ── GET /api/auth/reset-password (Responsive Web Interface & Deep Link Launcher)
router.get('/reset-password', async (req, res) => {
  const { token, email } = req.query;
  const rawToken = (token || '').trim();
  const rawEmail = (email || '').trim().toLowerCase();

  const customSchemeUrl = `quickbite://reset-password?token=${encodeURIComponent(rawToken)}&email=${encodeURIComponent(rawEmail)}`;
  const secondarySchemeUrl = `messq://reset-password?token=${encodeURIComponent(rawToken)}&email=${encodeURIComponent(rawEmail)}`;
  const intentUrl = `intent://reset-password?token=${encodeURIComponent(rawToken)}&email=${encodeURIComponent(rawEmail)}#Intent;scheme=quickbite;package=com.aistudio.campuscanteen.fxdz;end`;

  let isValid = false;
  let validationMessage = '';

  if (!rawToken || !rawEmail) {
    isValid = false;
    validationMessage = 'Missing reset token or email address. Please use the link provided in your email.';
  } else {
    try {
      const tokenHash = hashToken(rawToken);
      const resetRecord = await PasswordResetToken.findOne({
        email: rawEmail,
        tokenHash,
        used: false,
        expiresAt: { $gt: new Date() },
      });

      if (resetRecord) {
        isValid = true;
      } else {
        isValid = false;
        validationMessage = 'This password reset link is invalid or has expired. Please request a new link from the Quick Bite app.';
      }
    } catch (e) {
      console.error('[Auth Web Reset] Token validation error:', e);
      isValid = false;
      validationMessage = 'Unable to validate your reset link. Please try again or request a new link.';
    }
  }

  const html = `
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
  <title>Reset Password - Quick Bite</title>
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&display=swap" rel="stylesheet">
  <style>
    :root {
      --bg: #0C0D0E;
      --card-bg: #18181B;
      --border: #27272A;
      --primary: #FF6B00;
      --primary-hover: #E65100;
      --text: #FAFAFA;
      --text-muted: #A1A1AA;
      --text-dim: #71717A;
      --input-bg: #121214;
      --error-bg: #2A1215;
      --error-border: #7F1D1D;
      --error-text: #F87171;
      --success-bg: #0E291A;
      --success-border: #166534;
      --success-text: #4ADE80;
    }
    * {
      box-sizing: border-box;
      margin: 0;
      padding: 0;
      font-family: 'Plus Jakarta Sans', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
      -webkit-font-smoothing: antialiased;
    }
    body {
      background-color: var(--bg);
      color: var(--text);
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 24px 16px;
    }
    .container {
      width: 100%;
      max-width: 440px;
      background-color: var(--card-bg);
      border: 1px solid var(--border);
      border-radius: 18px;
      padding: 36px 32px;
      box-shadow: 0 20px 40px rgba(0, 0, 0, 0.55);
    }
    .brand-header {
      display: flex;
      align-items: center;
      gap: 12px;
      margin-bottom: 24px;
    }
    .brand-logo {
      width: 44px;
      height: 44px;
      background-color: var(--primary);
      border-radius: 12px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 24px;
      box-shadow: 0 4px 14px rgba(255, 107, 0, 0.35);
    }
    .brand-name {
      font-size: 19px;
      font-weight: 700;
      color: #FFFFFF;
      letter-spacing: -0.3px;
    }
    h1 {
      font-size: 24px;
      font-weight: 700;
      letter-spacing: -0.4px;
      margin-bottom: 8px;
      color: #FFFFFF;
    }
    p.desc {
      font-size: 14.5px;
      line-height: 22px;
      color: var(--text-muted);
      margin-bottom: 24px;
    }
    .user-pill {
      display: inline-block;
      background: #27272A;
      color: #E4E4E7;
      padding: 4px 10px;
      border-radius: 6px;
      font-size: 13px;
      font-weight: 500;
      word-break: break-all;
    }
    .form-group {
      margin-bottom: 20px;
      text-align: left;
    }
    label {
      display: block;
      font-size: 13.5px;
      font-weight: 600;
      color: #D4D4D8;
      margin-bottom: 8px;
    }
    .input-wrapper {
      position: relative;
      display: flex;
      align-items: center;
    }
    input {
      width: 100%;
      background: var(--input-bg);
      border: 1px solid var(--border);
      border-radius: 10px;
      padding: 13px 44px 13px 14px;
      font-size: 15px;
      color: #FAFAFA;
      outline: none;
      transition: border-color 0.2s, box-shadow 0.2s;
    }
    input:focus {
      border-color: var(--primary);
      box-shadow: 0 0 0 3px rgba(255, 107, 0, 0.18);
    }
    .toggle-visibility {
      position: absolute;
      right: 12px;
      background: none;
      border: none;
      color: var(--text-dim);
      font-size: 16px;
      cursor: pointer;
      padding: 6px;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .toggle-visibility:hover {
      color: var(--text);
    }
    .btn {
      width: 100%;
      background-color: var(--primary);
      color: #FFFFFF !important;
      font-size: 15px;
      font-weight: 600;
      text-decoration: none;
      padding: 14px;
      border-radius: 10px;
      border: none;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
      transition: background-color 0.2s, transform 0.1s;
      box-shadow: 0 4px 14px rgba(255, 107, 0, 0.3);
    }
    .btn:hover {
      background-color: var(--primary-hover);
    }
    .btn:active {
      transform: scale(0.99);
    }
    .btn:disabled {
      opacity: 0.65;
      cursor: not-allowed;
      transform: none;
    }
    .btn-secondary {
      background: transparent;
      border: 1px solid var(--border);
      color: var(--text-muted) !important;
      box-shadow: none;
      margin-top: 14px;
      font-size: 14px;
      padding: 12px;
    }
    .btn-secondary:hover {
      background: #27272A;
      color: #FAFAFA !important;
    }
    .alert {
      padding: 12px 14px;
      border-radius: 10px;
      font-size: 13.5px;
      line-height: 20px;
      margin-bottom: 20px;
      text-align: left;
    }
    .alert-error {
      background: var(--error-bg);
      border: 1px solid var(--error-border);
      color: var(--error-text);
    }
    .alert-success {
      background: var(--success-bg);
      border: 1px solid var(--success-border);
      color: var(--success-text);
    }
    .spinner {
      width: 18px;
      height: 18px;
      border: 2.5px solid rgba(255, 255, 255, 0.3);
      border-top-color: #FFFFFF;
      border-radius: 50%;
      animation: spin 0.7s linear infinite;
    }
    @keyframes spin {
      to { transform: rotate(360deg); }
    }
    .footer-note {
      font-size: 12.5px;
      line-height: 18px;
      color: var(--text-dim);
      margin-top: 24px;
      border-top: 1px solid var(--border);
      padding-top: 18px;
      text-align: left;
    }
  </style>
</head>
<body>
  <div class="container">
    <!-- Brand Header -->
    <div class="brand-header">
      <div class="brand-logo">🍔</div>
      <div class="brand-name">Quick Bite</div>
    </div>

    ${isValid ? `
    <!-- Valid Token View -->
    <div id="resetFormView">
      <h1>Reset your password</h1>
      <p class="desc">
        Enter a new secure password for <span class="user-pill">${rawEmail}</span>.
      </p>

      <div id="errorAlert" class="alert alert-error" style="display: none;"></div>

      <form id="resetForm" onsubmit="handleReset(event)">
        <div class="form-group">
          <label for="newPassword">New Password</label>
          <div class="input-wrapper">
            <input type="password" id="newPassword" placeholder="At least 6 characters" required autocomplete="new-password" minlength="6" />
            <button type="button" class="toggle-visibility" onclick="togglePass('newPassword', this)" title="Show or hide password">👁️</button>
          </div>
        </div>

        <div class="form-group">
          <label for="confirmPassword">Confirm Password</label>
          <div class="input-wrapper">
            <input type="password" id="confirmPassword" placeholder="Re-enter your new password" required autocomplete="new-password" minlength="6" />
            <button type="button" class="toggle-visibility" onclick="togglePass('confirmPassword', this)" title="Show or hide password">👁️</button>
          </div>
        </div>

        <button type="submit" id="submitBtn" class="btn">
          <span>Set New Password</span>
        </button>

        <button type="button" id="openAppBtn" class="btn btn-secondary" onclick="openApp()">
          📱 Open in Quick Bite App
        </button>
      </form>

      <div class="footer-note">
        After updating your password, you will be able to immediately sign in using your new credentials.
      </div>
    </div>

    <!-- Success View (Initially Hidden) -->
    <div id="successView" style="display: none; text-align: center;">
      <div style="width: 56px; height: 56px; border-radius: 50%; background: #0E291A; border: 1px solid #166534; color: #4ADE80; font-size: 28px; display: flex; align-items: center; justify-content: center; margin: 0 auto 20px;">
        ✓
      </div>
      <h1>Password Updated</h1>
      <p class="desc" style="margin-bottom: 28px;">
        Your password has been changed successfully. You can now open the Quick Bite app and log in with your new password.
      </p>

      <button type="button" class="btn" onclick="openApp()">
        📱 Open Quick Bite App
      </button>

      <div class="footer-note" style="text-align: center;">
        You can now close this browser tab.
      </div>
    </div>
    ` : `
    <!-- Invalid / Expired View -->
    <div style="text-align: center;">
      <div style="width: 56px; height: 56px; border-radius: 50%; background: #2A1215; border: 1px solid #7F1D1D; color: #F87171; font-size: 28px; display: flex; align-items: center; justify-content: center; margin: 0 auto 20px;">
        ✕
      </div>
      <h1>Link Expired or Invalid</h1>
      <p class="desc" style="margin-bottom: 28px;">
        ${validationMessage}
      </p>

      <button type="button" class="btn" onclick="openApp()">
        📱 Open Quick Bite App
      </button>

      <div class="footer-note" style="text-align: center;">
        If you need to reset your password, open the Quick Bite app, go to Login, and tap "Forgot Password?" to receive a fresh link.
      </div>
    </div>
    `}
  </div>

  <script>
    const primaryUrl = "${customSchemeUrl}";
    const intentUrl = "${intentUrl}";
    const secondaryUrl = "${secondarySchemeUrl}";
    const rawToken = "${rawToken}";
    const rawEmail = "${rawEmail}";

    function openApp() {
      window.location.replace(primaryUrl);
      setTimeout(function() {
        window.location.href = intentUrl;
      }, 400);
      setTimeout(function() {
        window.location.href = secondaryUrl;
      }, 1000);
    }

    function togglePass(inputId, btn) {
      const input = document.getElementById(inputId);
      if (input.type === 'password') {
        input.type = 'text';
        btn.textContent = '🙈';
      } else {
        input.type = 'password';
        btn.textContent = '👁️';
      }
    }

    async function handleReset(e) {
      e.preventDefault();
      const newPassword = document.getElementById('newPassword').value;
      const confirmPassword = document.getElementById('confirmPassword').value;
      const errorAlert = document.getElementById('errorAlert');
      const submitBtn = document.getElementById('submitBtn');

      errorAlert.style.display = 'none';

      if (!newPassword || newPassword.length < 6) {
        errorAlert.textContent = 'Password must be at least 6 characters long.';
        errorAlert.style.display = 'block';
        return;
      }

      if (newPassword !== confirmPassword) {
        errorAlert.textContent = 'Passwords do not match. Please re-enter.';
        errorAlert.style.display = 'block';
        return;
      }

      submitBtn.disabled = true;
      submitBtn.innerHTML = '<div class="spinner"></div><span>Updating Password...</span>';

      try {
        const response = await fetch('/api/auth/reset-password', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({
            token: rawToken,
            email: rawEmail,
            newPassword: newPassword,
          }),
        });

        const data = await response.json();

        if (!response.ok) {
          throw new Error(data.error || 'Failed to update password.');
        }

        document.getElementById('resetFormView').style.display = 'none';
        document.getElementById('successView').style.display = 'block';
      } catch (err) {
        errorAlert.textContent = err.message || 'An error occurred while resetting your password.';
        errorAlert.style.display = 'block';
        submitBtn.disabled = false;
        submitBtn.innerHTML = '<span>Set New Password</span>';
      }
    }
  </script>
</body>
</html>
  `.trim();

  res.send(html);
});

module.exports = router;
