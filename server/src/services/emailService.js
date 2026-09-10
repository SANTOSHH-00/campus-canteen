const nodemailer = require('nodemailer');

/**
 * Centralized Email Service for Quick Bite
 * Uses Nodemailer with Gmail SMTP (Port 465, SSL)
 */

const https = require('https');

let transporter = null;

function getTransporter() {
  if (!transporter) {
    const user = process.env.EMAIL_USER;
    const pass = process.env.EMAIL_APP_PASSWORD;

    transporter = nodemailer.createTransport({
      service: 'gmail',
      auth: {
        user: user || '',
        pass: pass ? pass.replace(/\s+/g, '') : '',
      },
      connectionTimeout: 5000,
      greetingTimeout: 5000,
      socketTimeout: 6000,
    });
  }
  return transporter;
}

// Brevo API (port 443 HTTPS - 300 free emails/day, no credit card required)
function sendViaBrevo(apiKey, { to, subject, html, text }) {
  const senderEmail = process.env.EMAIL_USER || 'quickbite.connecting@gmail.com';
  const data = JSON.stringify({
    sender: { name: 'Quick Bite Campus', email: senderEmail },
    to: [{ email: to.trim().toLowerCase() }],
    subject,
    htmlContent: html || text,
    textContent: text || '',
  });

  return new Promise((resolve, reject) => {
    const req = https.request({
      hostname: 'api.brevo.com',
      port: 443,
      path: '/v3/smtp/email',
      method: 'POST',
      headers: {
        'api-key': apiKey,
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(data),
      },
      timeout: 6000,
    }, (res) => {
      let body = '';
      res.on('data', chunk => body += chunk);
      res.on('end', () => {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          console.log(`[EmailService] Delivered successfully via Brevo HTTPS API to ${to}.`);
          resolve({ success: true, messageId: body });
        } else {
          console.error(`[EmailService] Brevo API error (${res.statusCode}):`, body);
          reject(new Error(`Brevo API error: ${body}`));
        }
      });
    });
    req.on('error', reject);
    req.on('timeout', () => { req.destroy(); reject(new Error('Brevo API timeout')); });
    req.write(data);
    req.end();
  });
}

// Resend API (port 443 HTTPS - 3,000 free emails/month)
function sendViaResend(apiKey, { to, subject, html, text }) {
  const sender = process.env.RESEND_FROM || 'onboarding@resend.dev';
  const data = JSON.stringify({
    from: `Quick Bite <${sender}>`,
    to: [to.trim().toLowerCase()],
    subject,
    html: html || text,
    text: text || '',
  });

  return new Promise((resolve, reject) => {
    const req = https.request({
      hostname: 'api.resend.com',
      port: 443,
      path: '/emails',
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${apiKey}`,
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(data),
      },
      timeout: 6000,
    }, (res) => {
      let body = '';
      res.on('data', chunk => body += chunk);
      res.on('end', () => {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          console.log(`[EmailService] Delivered successfully via Resend HTTPS API to ${to}.`);
          resolve({ success: true, messageId: body });
        } else {
          console.error(`[EmailService] Resend API error (${res.statusCode}):`, body);
          reject(new Error(`Resend API error: ${body}`));
        }
      });
    });
    req.on('error', reject);
    req.on('timeout', () => { req.destroy(); reject(new Error('Resend API timeout')); });
    req.write(data);
    req.end();
  });
}

/**
 * Generic reusable email sender with multi-provider fallback (Brevo / Resend / Gmail SMTP)
 * @param {Object} options - { to, subject, html, text }
 */
async function sendEmail({ to, subject, html, text }) {
  const recipient = to.trim().toLowerCase();

  // 1. Check for Brevo API key (HTTPS port 443 - works on Render Free Tier)
  if (process.env.BREVO_API_KEY) {
    try {
      return await sendViaBrevo(process.env.BREVO_API_KEY.trim(), { to: recipient, subject, html, text });
    } catch (e) {
      console.warn('[EmailService] Brevo HTTPS dispatch failed, attempting next provider...', e.message);
    }
  }

  // 2. Check for Resend API key (HTTPS port 443 - works on Render Free Tier)
  if (process.env.RESEND_API_KEY) {
    try {
      return await sendViaResend(process.env.RESEND_API_KEY.trim(), { to: recipient, subject, html, text });
    } catch (e) {
      console.warn('[EmailService] Resend HTTPS dispatch failed, attempting next provider...', e.message);
    }
  }

  // 3. Fallback to Gmail SMTP (Works on local machine or paid clouds)
  const user = process.env.EMAIL_USER;
  const pass = process.env.EMAIL_APP_PASSWORD;

  if (!user || !pass) {
    console.warn(`[EmailService] EMAIL_USER / EMAIL_APP_PASSWORD not set. Skipping SMTP to ${recipient}.`);
    return { success: false, skipped: true, error: 'Email credentials not configured' };
  }

  const fromAddress = user || 'no-reply@quickbite.campus';
  const mailOptions = {
    from: `"Quick Bite Campus" <${fromAddress}>`,
    to: recipient,
    subject,
    text: text || '',
    html: html || '',
  };

  try {
    const transport = getTransporter();
    const sendPromise = transport.sendMail(mailOptions);
    const timeoutPromise = new Promise((_, reject) =>
      setTimeout(() => reject(new Error('Gmail SMTP timed out (Render free tier blocks ports 465/587)')), 6000)
    );

    const info = await Promise.race([sendPromise, timeoutPromise]);
    console.log(`[EmailService] Email sent successfully to ${recipient}. MessageId: ${info.messageId}`);
    return { success: true, messageId: info.messageId };
  } catch (error) {
    console.error(`[EmailService] SMTP delivery failed to ${recipient}: ${error.message}`);
    return { success: false, error: error.message };
  }
}

/**
 * Sends 6-digit verification OTP email to Canteen Owner
 * @param {string} toEmail 
 * @param {string} otpCode 
 * @param {string} ownerName 
 */
async function sendOwnerOTP(toEmail, otpCode, ownerName = 'Canteen Owner') {
  const subject = `Quick Bite - Owner Verification Code: ${otpCode}`;

  const html = `
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Owner Login Verification</title>
  <style>
    body {
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
      background-color: #0F172A;
      margin: 0;
      padding: 32px 16px;
      color: #1E293B;
    }
    .card {
      max-width: 480px;
      margin: 0 auto;
      background: #FFFFFF;
      border-radius: 20px;
      overflow: hidden;
      box-shadow: 0 10px 30px rgba(0, 0, 0, 0.25);
    }
    .header {
      background: linear-gradient(135deg, #FF6B00 0%, #E65100 100%);
      padding: 32px 24px;
      text-align: center;
      color: #FFFFFF;
    }
    .header h1 {
      margin: 0;
      font-size: 26px;
      font-weight: 800;
      letter-spacing: -0.5px;
    }
    .header p {
      margin: 6px 0 0;
      font-size: 13px;
      font-weight: 600;
      letter-spacing: 1.5px;
      text-transform: uppercase;
      opacity: 0.9;
    }
    .content {
      padding: 36px 28px;
      text-align: center;
    }
    .greeting {
      font-size: 17px;
      font-weight: 600;
      color: #0F172A;
      margin-bottom: 8px;
    }
    .subtitle {
      font-size: 14px;
      color: #64748B;
      line-height: 22px;
      margin-bottom: 28px;
    }
    .otp-container {
      background: #FFF7ED;
      border: 2px dashed #FF6B00;
      border-radius: 16px;
      padding: 20px 24px;
      display: inline-block;
      margin: 0 auto 28px;
    }
    .otp-code {
      font-size: 38px;
      font-weight: 800;
      letter-spacing: 10px;
      color: #C2410C;
      margin: 0;
      padding-left: 10px;
      font-family: 'Courier New', monospace;
    }
    .notice {
      background: #F8FAFC;
      border-radius: 12px;
      padding: 16px;
      font-size: 13px;
      color: #475569;
      line-height: 20px;
      margin-bottom: 12px;
    }
    .notice strong {
      color: #D97706;
    }
    .footer {
      background: #F1F5F9;
      padding: 20px 24px;
      text-align: center;
      font-size: 12px;
      color: #94A3B8;
      border-top: 1px solid #E2E8F0;
    }
  </style>
</head>
<body>
  <div class="card">
    <div class="header">
      <h1>Quick Bite</h1>
      <p>Owner Login Verification</p>
    </div>
    <div class="content">
      <div class="greeting">Hello, ${ownerName} 👋</div>
      <div class="subtitle">
        Your verification code to access your Canteen Owner Dashboard is:
      </div>
      <div class="otp-container">
        <div class="otp-code">${otpCode}</div>
      </div>
      <div class="notice">
        ⏰ <strong>This OTP will expire in 5 minutes.</strong><br>
        If you did not attempt to log in, please ignore this email.<br>
        <strong>Do not share this code with anyone.</strong>
      </div>
    </div>
    <div class="footer">
      Quick Bite Food Ordering • Campus Canteen System
    </div>
  </div>
</body>
</html>
  `.trim();

  const text = `
Quick Bite - Owner Login Verification

Hello ${ownerName},

Your verification code is:
${otpCode}

This OTP will expire in 5 minutes.
If you did not attempt to log in, please ignore this email.
Do not share this code with anyone.

Quick Bite Campus Canteen System
  `.trim();

  return sendEmail({ to: toEmail, subject, html, text });
}

/**
 * Sends Password Reset Email to User
 * @param {string} toEmail 
 * @param {string} resetUrl 
 * @param {string} userName 
 */
async function sendPasswordResetEmail(toEmail, resetUrl, userName = 'Student') {
  const subject = 'Quick Bite - Reset Your Password';

  const html = `
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Reset Your Password</title>
  <style>
    body {
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
      background-color: #0F172A;
      margin: 0;
      padding: 32px 16px;
      color: #1E293B;
    }
    .card {
      max-width: 480px;
      margin: 0 auto;
      background: #FFFFFF;
      border-radius: 20px;
      overflow: hidden;
      box-shadow: 0 10px 30px rgba(0, 0, 0, 0.25);
    }
    .header {
      background: linear-gradient(135deg, #1E293B 0%, #0F172A 100%);
      padding: 32px 24px;
      text-align: center;
      color: #FFFFFF;
    }
    .header h1 {
      margin: 0;
      font-size: 26px;
      font-weight: 800;
      letter-spacing: -0.5px;
    }
    .header p {
      margin: 6px 0 0;
      font-size: 13px;
      font-weight: 600;
      letter-spacing: 1.5px;
      text-transform: uppercase;
      color: #FF6B00;
    }
    .content {
      padding: 36px 28px;
      text-align: center;
    }
    .greeting {
      font-size: 17px;
      font-weight: 600;
      color: #0F172A;
      margin-bottom: 8px;
    }
    .subtitle {
      font-size: 14px;
      color: #64748B;
      line-height: 22px;
      margin-bottom: 28px;
    }
    .btn {
      display: inline-block;
      background: #FF6B00;
      color: #FFFFFF !important;
      font-size: 15px;
      font-weight: 700;
      text-decoration: none;
      padding: 14px 34px;
      border-radius: 30px;
      box-shadow: 0 4px 14px rgba(255, 107, 0, 0.35);
      margin-bottom: 26px;
    }
    .notice {
      background: #F8FAFC;
      border-radius: 12px;
      padding: 16px;
      font-size: 13px;
      color: #475569;
      line-height: 20px;
      margin-bottom: 12px;
    }
    .footer {
      background: #F1F5F9;
      padding: 20px 24px;
      text-align: center;
      font-size: 12px;
      color: #94A3B8;
      border-top: 1px solid #E2E8F0;
    }
    .link-alt {
      font-size: 11.5px;
      color: #94A3B8;
      word-break: break-all;
      margin-top: 14px;
    }
  </style>
</head>
<body>
  <div class="card">
    <div class="header">
      <h1>Quick Bite</h1>
      <p>Reset Your Password</p>
    </div>
    <div class="content">
      <div class="greeting">Hello, ${userName} 👋</div>
      <div class="subtitle">
        We received a request to reset your Quick Bite password.<br>
        Click the button below to open the <strong>Quick Bite app</strong> and set your new password:
      </div>
      <a href="${resetUrl}" class="btn" target="_blank">📱 Open Quick Bite App</a>
      <div class="notice">
        ⏰ This link will expire in 20 minutes.<br>
        Tapping the link will automatically open the Quick Bite app on your device where you can set your new password.
      </div>
      <div class="link-alt">
        Direct link: <a href="${resetUrl}" style="color: #FF6B00;">${resetUrl}</a>
      </div>
    </div>
    <div class="footer">
      Quick Bite Food Ordering • Campus Canteen System
    </div>
  </div>
</body>
</html>
  `.trim();

  const text = `
Quick Bite - Reset Your Password

Hello ${userName},

We received a request to reset your Quick Bite password.
Use the following link to open the Quick Bite app and set your new password:
${resetUrl}

This link will expire in 20 minutes.
If you did not request a password reset, you can safely ignore this email.

Quick Bite Campus Canteen System
  `.trim();

  return sendEmail({ to: toEmail, subject, html, text });
}

module.exports = {
  sendEmail,
  sendOwnerOTP,
  sendPasswordResetEmail,
};
