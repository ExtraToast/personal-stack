package com.jorisjonkers.personalstack.auth.infrastructure.email

object AuthEmailTemplates {
    fun welcomeEmail(username: String): Pair<String, String> {
        val textBody =
            """
            |Hi $username,
            |
            |Your account has been created on jorisjonkers.dev.
            |
            |Next step: Log in and set up two-factor authentication (TOTP)
            |to activate your account.
            |
            |https://auth.jorisjonkers.dev/login
            |
            |— jorisjonkers.dev
            """.trimMargin()

        val htmlBody =
            """
            |<!DOCTYPE html>
            |<html lang="en">
            |<head><meta charset="UTF-8"></head>
            |<body style="margin:0;padding:0;background-color:#0d1117;font-family:'SF Mono',Monaco,'Cascadia Code',monospace;">
            |  <table width="100%" cellpadding="0" cellspacing="0" style="background-color:#0d1117;padding:40px 20px;">
            |    <tr><td align="center">
            |      <table width="560" cellpadding="0" cellspacing="0" style="background-color:#161b22;border:1px solid #30363d;border-radius:12px;overflow:hidden;">
            |        <!-- Header -->
            |        <tr><td style="padding:24px 32px 16px;border-bottom:1px solid #30363d;">
            |          <span style="display:inline-block;width:10px;height:10px;border-radius:50%;background:#ff5f56;margin-right:6px;"></span>
            |          <span style="display:inline-block;width:10px;height:10px;border-radius:50%;background:#ffbd2e;margin-right:6px;"></span>
            |          <span style="display:inline-block;width:10px;height:10px;border-radius:50%;background:#27c93f;margin-right:12px;"></span>
            |          <span style="color:#6e7681;font-size:12px;">~/auth/welcome</span>
            |        </td></tr>
            |        <!-- Body -->
            |        <tr><td style="padding:32px;">
            |          <h1 style="color:#e6edf3;font-size:20px;font-weight:600;margin:0 0 16px;">Welcome, $username</h1>
            |          <p style="color:#8b949e;font-size:14px;line-height:1.6;margin:0 0 24px;">
            |            Your account has been created successfully.
            |          </p>
            |          <p style="color:#8b949e;font-size:14px;line-height:1.6;margin:0 0 24px;">
            |            To activate your account, please log in and set up<br>
            |            two-factor authentication (TOTP) with your authenticator app.
            |          </p>
            |          <table cellpadding="0" cellspacing="0" style="margin:0 0 24px;">
            |            <tr><td style="background-color:#238636;border-radius:6px;padding:10px 24px;">
            |              <a href="https://auth.jorisjonkers.dev/login" style="color:#ffffff;text-decoration:none;font-size:14px;font-weight:600;">
            |                Log in &amp; set up 2FA
            |              </a>
            |            </td></tr>
            |          </table>
            |          <p style="color:#484f58;font-size:12px;line-height:1.5;margin:0;">
            |            If you didn't create this account, you can safely ignore this email.
            |          </p>
            |        </td></tr>
            |        <!-- Footer -->
            |        <tr><td style="padding:16px 32px;border-top:1px solid #30363d;text-align:center;">
            |          <span style="color:#484f58;font-size:11px;">jorisjonkers.dev</span>
            |        </td></tr>
            |      </table>
            |    </td></tr>
            |  </table>
            |</body>
            |</html>
            """.trimMargin()

        return Pair(textBody, htmlBody)
    }
}
