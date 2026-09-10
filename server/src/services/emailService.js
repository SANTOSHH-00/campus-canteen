const nodemailer = require('nodemailer');

/**
 * Centralized Email Service for Quick Bite
 * Uses Nodemailer with Gmail SMTP (Port 465, SSL)
 */

let transporter = null;

function getTransporter() {
  if (!transporter) {
    const user = process.env.EMAIL_USER;
    const pass = process.env.EMAIL_APP_PASSWORD;

    transporter = nodemailer.createTransport({
      host: 'smtp.gmail.com',
      port: 465,
      secure: true, // SSL
      auth: {
        user: user || '',
        pass: pass ? pass.replace(/\s+/g, '') : '', // strip accidental spaces in app password
      },
    });
  }
  return transporter;
}

/**
 * Generic reusable email sender
 * @param {Object} options - { to, subject, html, text }
 */
async function sendEmail({ to, subject, html, text }) {
  const fromAddress = process.env.EMAIL_USER || 'no-reply@quickbite.campus';
  const mailOptions = {
    from: `"Quick Bite Campus" <${fromAddress}>`,
    to: to.trim().toLowerCase(),
    subject,
    text: text || '',
    html: html || '',
  };

  try {
    const transport = getTransporter();
    const info = await transport.sendMail(mailOptions);
    console.log(`[EmailService] Email sent successfully to ${to}. MessageId: ${info.messageId}`);
    return { success: true, messageId: info.messageId };
  } catch (error) {
    console.error(`[EmailService] Failed to send email to ${to}:`, error.message);
    throw error;
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
