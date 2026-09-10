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

let cachedBrevoSender = null;

// Dynamically fetch the verified sender email registered in the Brevo account
async function getBrevoSenderEmail(apiKey) {
  if (cachedBrevoSender) return cachedBrevoSender;
  const configured = (process.env.BREVO_SENDER_EMAIL || process.env.EMAIL_USER || '').trim();
  if (configured) {
    console.log(`[EmailService] Using configured sender email: ${configured}`);
    cachedBrevoSender = configured;
    return cachedBrevoSender;
  }

  return new Promise((resolve) => {
    const req = https.request({
      hostname: 'api.brevo.com',
      port: 443,
      path: '/v3/senders',
      method: 'GET',
      headers: {
        'api-key': apiKey,
      },
      timeout: 5000,
    }, (res) => {
      let body = '';
      res.on('data', chunk => body += chunk);
      res.on('end', () => {
        try {
          const json = JSON.parse(body);
          if (json.senders && Array.isArray(json.senders) && json.senders.length > 0) {
            const active = json.senders.find(s => s.active) || json.senders[0];
            if (active && active.email) {
              console.log(`[EmailService] Auto-detected active Brevo sender: ${active.email}`);
              cachedBrevoSender = active.email;
              resolve(active.email);
              return;
            }
          }
        } catch (e) {
          console.warn('[EmailService] Failed to parse Brevo senders list:', e.message);
        }
        resolve(process.env.EMAIL_USER || 'quickbites.connecting@gmail.com');
      });
    });
    req.on('error', (err) => {
      console.warn('[EmailService] Error fetching Brevo senders:', err.message);
      resolve(process.env.EMAIL_USER || 'quickbites.connecting@gmail.com');
    });
    req.end();
  });
}

// Brevo API (port 443 HTTPS - 300 free emails/day, no credit card required)
async function sendViaBrevo(apiKey, { to, subject, html, text }) {
  const senderEmail = await getBrevoSenderEmail(apiKey);
  console.log(`[EmailService] Attempting Brevo HTTPS send from <${senderEmail}> to <${to}>`);

  const data = JSON.stringify({
    sender: { name: 'QuickBite', email: senderEmail },
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
          console.log(`[EmailService] Delivered successfully via Brevo HTTPS API to ${to}. Response:`, body);
          resolve({ success: true, messageId: body });
        } else {
          console.error(`[EmailService] Brevo API rejected email (${res.statusCode}): ${body}`);
          reject(new Error(`Brevo API error (${res.statusCode}): ${body}`));
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
 * Unified Quick Bite Email Template Generator
 * Follows the minimalist, dark-themed, trustworthy design of Reference Image 1.
 */
function buildQuickBiteEmailHtml({ title, bodyHtml, code, ctaUrl, ctaText, expiryText, securityNote }) {
  return `
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>${title}</title>
</head>
<body style="margin: 0; padding: 32px 16px; background-color: #0C0D0E; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; color: #FAFAFA; -webkit-font-smoothing: antialiased;">
  <div style="max-width: 440px; margin: 0 auto; background-color: #18181B; border: 1px solid #27272A; border-radius: 16px; padding: 36px 32px; box-sizing: border-box;">
    
    <!-- Quick Bite App Icon & Brand Name -->
    <table cellpadding="0" cellspacing="0" border="0" style="margin-bottom: 26px;">
      <tr>
        <td style="width: 44px; height: 44px; background-color: #FF6B00; border-radius: 12px; text-align: center; vertical-align: middle;">
          <span style="font-size: 24px; line-height: 44px;">🍔</span>
        </td>
        <td style="padding-left: 12px; vertical-align: middle;">
          <span style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; font-size: 19px; font-weight: 700; color: #FFFFFF; letter-spacing: -0.3px;">Quick Bite</span>
        </td>
      </tr>
    </table>

    <!-- Email Title -->
    <h1 style="margin: 0 0 16px 0; font-size: 24px; font-weight: 700; color: #FFFFFF; letter-spacing: -0.4px; line-height: 32px;">
      ${title}
    </h1>

    <!-- Email Body Explanation -->
    <div style="font-size: 15px; line-height: 24px; color: #A1A1AA; margin-bottom: 24px;">
      ${bodyHtml}
    </div>

    <!-- OTP Code Display (for OTP emails) -->
    ${code ? `
    <div style="margin: 28px 0; text-align: left;">
      <div style="font-family: 'SF Mono', 'Fira Code', 'Roboto Mono', Consolas, monospace; font-size: 38px; font-weight: 700; letter-spacing: 6px; color: #FFFFFF; user-select: all; -webkit-user-select: all;">
        ${code}
      </div>
    </div>
    ` : ''}

    <!-- CTA Button (for Password Reset & Link emails) -->
    ${ctaUrl ? `
    <div style="margin: 28px 0 20px 0;">
      <a href="${ctaUrl}" target="_blank" style="display: inline-block; background-color: #FF6B00; color: #FFFFFF !important; font-size: 15px; font-weight: 600; text-decoration: none; padding: 13px 28px; border-radius: 10px; box-sizing: border-box;">
        ${ctaText || 'Reset password'}
      </a>
    </div>
    <div style="font-size: 12px; color: #71717A; line-height: 18px; word-break: break-all; margin-bottom: 24px;">
      Direct link: <a href="${ctaUrl}" style="color: #FF6B00; text-decoration: none;">${ctaUrl}</a>
    </div>
    ` : ''}

    <!-- Clean Divider Line -->
    <hr style="border: none; border-top: 1px solid #27272A; margin: 28px 0 20px 0;">

    <!-- Expiry Notice -->
    <div style="font-size: 13px; line-height: 20px; color: #71717A; margin-bottom: 10px;">
      ${expiryText}
    </div>

    <!-- Security Footnote -->
    <div style="font-size: 13px; line-height: 20px; color: #71717A;">
      ${securityNote || "If you didn't request this, you can safely ignore this email. Someone else might have typed your email address by mistake."}
    </div>

  </div>
</body>
</html>
  `.trim();
}

/**
 * Sends 6-digit verification OTP email to Canteen Owner
 * @param {string} toEmail 
 * @param {string} otpCode 
 * @param {string} ownerName 
 */
async function sendOwnerOTP(toEmail, otpCode, ownerName = 'Canteen Owner') {
  const subject = `Quick Bite - Owner Verification Code: ${otpCode}`;

  const html = buildQuickBiteEmailHtml({
    title: 'Verify your email',
    bodyHtml: `We received a request to access your Quick Bite Canteen Owner account for <strong style="color: #FAFAFA;">${toEmail}</strong>. Enter the verification code below in your Quick Bite app:`,
    code: otpCode,
    expiryText: 'This code expires in 5 minutes.',
    securityNote: "If you didn't attempt to log in to Quick Bite, you can safely ignore this email. Someone else might have typed your email address by mistake.",
  });

  const text = `Quick Bite - Owner Verification\n\nWe received a request to access your Quick Bite Canteen Owner account for ${toEmail}.\n\nVerification Code: ${otpCode}\n\nThis code expires in 5 minutes.\nIf you did not request this code, you can safely ignore this email.`;

  return sendEmail({ to: toEmail, subject, html, text });
}

/**
 * Sends 6-digit account verification OTP
 * @param {string} toEmail
 * @param {string} otpCode
 * @param {string} userName
 */
async function sendAccountVerificationOTP(toEmail, otpCode, userName = 'User') {
  const subject = `Quick Bite - Verification Code: ${otpCode}`;

  const html = buildQuickBiteEmailHtml({
    title: 'Verify your email',
    bodyHtml: `We need to verify your email address <strong style="color: #FAFAFA;">${toEmail}</strong> before you can access your Quick Bite account. Enter the code below in your open app:`,
    code: otpCode,
    expiryText: 'This code expires in 10 minutes.',
    securityNote: "If you didn't sign up for Quick Bite, you can safely ignore this email. Someone else might have typed your email address by mistake.",
  });

  const text = `Quick Bite - Verify Your Email\n\nWe need to verify your email address ${toEmail} before you can access your account.\n\nVerification Code: ${otpCode}\n\nThis code expires in 10 minutes.\nIf you didn't sign up for Quick Bite, you can safely ignore this email.`;

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

  const html = buildQuickBiteEmailHtml({
    title: 'Reset your password',
    bodyHtml: `We received a request to reset the password for your Quick Bite account <strong style="color: #FAFAFA;">${toEmail}</strong>. Click the button below to set a new password:`,
    ctaUrl: resetUrl,
    ctaText: 'Reset password',
    expiryText: 'This link expires in 20 minutes.',
    securityNote: "If you didn't request a password reset, you can safely ignore this email. Your password will remain unchanged.",
  });

  const text = `Quick Bite - Reset Your Password\n\nWe received a request to reset your Quick Bite password for ${toEmail}.\n\nUse the link below to set your new password:\n${resetUrl}\n\nThis link expires in 20 minutes.\nIf you didn't request a password reset, you can safely ignore this email.`;

  return sendEmail({ to: toEmail, subject, html, text });
}

module.exports = {
  sendEmail,
  sendOwnerOTP,
  sendAccountVerificationOTP,
  sendPasswordResetEmail,
};
