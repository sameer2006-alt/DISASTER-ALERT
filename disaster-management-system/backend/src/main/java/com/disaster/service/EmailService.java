package com.disaster.service;

import com.disaster.model.DisasterEvent;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.mail.from:${spring.mail.username:}}")
    private String fromEmail;

    @Value("${app.mail.from-name:Smart Disaster Alert}")
    private String fromName;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    @Value("${spring.mail.host:smtp.example.org}")
    private String mailHost;

    @Value("${app.mail.provider:auto}")
    private String mailProvider;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * OTP must be synchronous so registration fails loudly if email cannot be sent.
     */
    public void sendOtpEmail(String to, String otp) throws EmailDeliveryException {
        log.info("════════════════════════════════════════════════════════════");
        log.info("🔑 [OTP GENERATED] Email: {} | CODE: {} (Valid 5 mins)", to, otp);
        log.info("════════════════════════════════════════════════════════════");

        String subject = otp + " is your verification code - Smart Disaster Alert";
        String html = """
                <div style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;max-width:520px;margin:20px auto;padding:32px;background:#ffffff;color:#1e293b;border:1px solid #e2e8f0;border-radius:16px;box-shadow:0 4px 16px rgba(0,0,0,0.06);">
                  <div style="text-align:center;margin-bottom:24px;">
                    <img src="cid:disasterLogo" alt="Smart Disaster Alert" style="width:100px;height:100px;border-radius:18px;box-shadow:0 6px 18px rgba(0,80,255,0.15);" />
                    <h2 style="color:#0f172a;margin:14px 0 4px;font-size:24px;font-weight:700;">Smart Disaster Alert</h2>
                    <p style="color:#64748b;margin:0;font-size:13px;font-weight:500;">Emergency Response &amp; Resource Coordination Network</p>
                  </div>
                  <hr style="border:none;border-top:1px solid #e2e8f0;margin:20px 0;">
                  <h3 style="color:#0f172a;margin-top:0;font-size:18px;font-weight:600;">Verify your email address</h3>
                  <p style="font-size:14px;color:#475569;line-height:1.5;">Thank you for registering. Use this 6-digit verification code to complete your signup:</p>
                  <div style="background:#f8fafc;border:2px dashed #93c5fd;border-radius:10px;padding:18px;text-align:center;margin:24px 0;">
                    <span style="font-size:36px;font-family:Consolas,monospace;letter-spacing:10px;font-weight:700;color:#2563eb;">%s</span>
                  </div>
                  <p style="font-size:13px;color:#64748b;line-height:1.5;">This code will expire in <strong>5 minutes</strong>. If you did not request this email, please safely disregard it.</p>
                  <hr style="border:none;border-top:1px solid #e2e8f0;margin:24px 0;">
                  <p style="font-size:12px;color:#94a3b8;margin:0;text-align:center;">Smart Disaster Alert &amp; Emergency Coordination Network</p>
                </div>
                """.formatted(otp);

        String text = "Your Smart Disaster Alert verification code is: " + otp + "\n\nThis code expires in 5 minutes.\n\nIf you did not request this, please ignore this email.";

        deliverSync(to, subject, text, html);
        log.info("OTP email delivered to {}", to);
    }

    @Async
    public void sendDisasterAlert(String to, DisasterEvent event, String nearestShelter) {
        String subject = "EMERGENCY: " + event.getDisasterType() + " Alert";
        String text = String.format(
                "EMERGENCY ALERT%n%nType: %s%nSeverity: %d/10%nLocation: %s%nMessage: %s%n%nNearest shelter: %s%n%nFollow local emergency instructions immediately.",
                event.getDisasterType(), event.getSeverity(), event.getLocation(),
                event.getMessage(), nearestShelter != null ? nearestShelter : "Check dashboard");
        try {
            deliverSync(to, subject, text, null);
        } catch (EmailDeliveryException e) {
            log.error("Disaster alert email failed for {}: {}", to, e.getMessage());
        }
    }

    @Async
    public void sendOrgDisasterAlert(String to, DisasterEvent event, List<String> requiredResources) {
        String subject = "RESCUE DEPLOYMENT REQUEST";
        String resourcesStr = String.join(", ", requiredResources);
        String html = """
                <div style="font-family:Arial,sans-serif;max-width:550px;margin:0 auto;padding:24px;background:#0d0d0d;color:#fff;border-radius:16px;">
                  <h2 style="color:#FF6A00;margin:0 0 16px;text-transform:uppercase;">RESCUE DEPLOYMENT REQUEST</h2>
                  <p>A new disaster event has occurred in your zone. Please coordinate resource deployment.</p>
                  <div style="background:rgba(255,255,255,0.05);padding:16px;border-radius:12px;margin:20px 0;">
                    <p><strong>Disaster Type:</strong> %s</p>
                    <p><strong>Severity:</strong> %d/10</p>
                    <p><strong>Location:</strong> %s</p>
                    <p><strong>Coordinates:</strong> %f, %f (Radius: %.1f km)</p>
                    <p><strong>Required Resources:</strong> <span style="color:#ffcc00;font-weight:bold;">%s</span></p>
                  </div>
                  <p>Please log into your dashboard to accept rescue missions and activate shelters.</p>
                </div>
                """.formatted(event.getDisasterType(), event.getSeverity(), event.getLocation(),
                event.getLatitude(), event.getLongitude(), event.getAffectedRadius(), resourcesStr);

        String text = String.format(
                "RESCUE DEPLOYMENT REQUEST%n%nType: %s%nSeverity: %d/10%nLocation: %s%nCoordinates: %f, %f%nAffected Radius: %.1f km%nRequired Resources: %s",
                event.getDisasterType(), event.getSeverity(), event.getLocation(),
                event.getLatitude(), event.getLongitude(), event.getAffectedRadius(), resourcesStr);
        try {
            deliverSync(to, subject, text, html);
            log.info("Org disaster alert email delivered to {}", to);
        } catch (EmailDeliveryException e) {
            log.error("Org disaster alert email failed for {}: {}", to, e.getMessage());
        }
    }

    @Async
    public void sendSosAlertToOrganisation(String to, com.disaster.model.RescueRequest request) {
        String subject = "EMERGENCY SOS: Citizen Needs Rescue near Your Location";
        String html = """
                <div style="font-family:Arial,sans-serif;max-width:550px;margin:0 auto;padding:24px;background:#0d0202;color:#fff;border:2px solid #ff3333;border-radius:16px;">
                  <h2 style="color:#ff3333;margin:0 0 16px;text-transform:uppercase;letter-spacing:1px;text-align:center;">🔴 CRITICAL SOS SIGNAL 🔴</h2>
                  <p style="color:rgba(255,255,255,0.8);font-size:14px;line-height:1.5;">An emergency SOS beacon has been broadcasted by a citizen near your operational sector. Immediate response is requested.</p>
                  
                  <div style="background:rgba(255,255,255,0.05);padding:16px;border-radius:12px;margin:20px 0;border-left:4px solid #ff3333;">
                    <p style="margin:4px 0;font-size:13px;color:rgba(255,255,255,0.5);"><strong>URGENCY PRIORITY:</strong> <span style="color:#ff9900;font-weight:bold;">%s</span></p>
                    <p style="margin:4px 0;font-size:13px;color:rgba(255,255,255,0.5);"><strong>COORDINATES:</strong> <span style="color:#fff;">%f, %f</span></p>
                    <p style="margin:12px 0 4px;font-size:13px;color:rgba(255,255,255,0.5);"><strong>EMERGENCY DESCRIPTION:</strong></p>
                    <p style="margin:0;font-size:14px;color:#fff;font-style:italic;">"%s"</p>
                  </div>
                  
                  <p style="color:rgba(255,255,255,0.7);font-size:13px;line-height:1.5;">Please log into the Disaster Management Portal to coordinate dispatch teams and shelters to assist this person.</p>
                  <hr style="border:none;border-top:1px solid rgba(255,51,51,0.2);margin:24px 0;">
                  <p style="color:rgba(255,255,255,0.4);font-size:11px;text-align:center;">Smart Disaster Alert &amp; Emergency Response Network</p>
                </div>
                """.formatted(request.getPriority(), request.getLatitude(), request.getLongitude(), request.getDescription());

        String text = String.format(
                "CRITICAL SOS ALERT%n%nPriority: %s%nCoordinates: %f, %f%nDescription: %s%n%nPlease respond immediately.",
                request.getPriority(), request.getLatitude(), request.getLongitude(), request.getDescription());
        try {
            deliverSync(to, subject, text, html);
            log.info("SOS alert email delivered to organisation: {}", to);
        } catch (EmailDeliveryException e) {
            log.error("SOS alert email failed for organisation {}: {}", to, e.getMessage());
        }
    }

    @Value("${GMAIL_USER:${spring.mail.username:}}")
    private String gmailUser;

    private void deliverSync(String to, String subject, String text, String html) throws EmailDeliveryException {
        String cleanFrom = (fromEmail != null) ? fromEmail.replace("\"", "").replace("'", "").trim() : "";
        String senderEmail = (gmailUser != null && !gmailUser.isBlank()) 
                ? gmailUser.replace("\"", "").replace("'", "").trim() 
                : cleanFrom;

        if (senderEmail.isBlank()) {
            throw new EmailDeliveryException("Mail sender address (MAIL_FROM / GMAIL_USER) is not configured");
        }

        Exception lastError = null;

        // Prioritize custom/configured SMTP using the configured App Password
        if (hasGmailCredentials()) {
            String targetHost = (mailHost != null && !mailHost.isBlank()) ? mailHost : "smtp.example.org";
            try {
                sendViaSmtp(to, subject, text, html, targetHost, senderEmail, getGmailPassword());
                log.info("Email sent successfully via SMTP to {}", to);
                return;
            } catch (Exception e) {
                lastError = e;
                log.warn("SMTP primary attempt failed: {}. Retrying without spaces...", e.getMessage());
                try {
                    sendViaSmtp(to, subject, text, html, targetHost, senderEmail, getGmailPassword().replace(" ", ""));
                    log.info("Email sent successfully via SMTP (no-spaces) to {}", to);
                    return;
                } catch (Exception e2) {
                    lastError = e2;
                    log.error("SMTP retry also failed: {}", e2.getMessage());
                }
            }
        }

        if (useBrevoApi()) {
            try {
                sendViaBrevoApi(to, subject, text, html);
                return;
            } catch (Exception e) {
                lastError = e;
                log.warn("Brevo API failed: {}", e.getMessage());
            }
        }

        try {
            sendViaSmtp(to, subject, text, html, mailHost, resolveSmtpUsername(), mailPassword);
        } catch (Exception e) {
            throw new EmailDeliveryException(
                    "Could not send email to " + to + ". Details: "
                            + (lastError != null ? lastError.getMessage() : e.getMessage()), e);
        }
    }

    private void sendViaBrevoApi(String to, String subject, String text, String html) {
        String apiKey = (brevoApiKey != null && !brevoApiKey.isBlank()) ? brevoApiKey : mailPassword;
        if (apiKey == null || !apiKey.startsWith("xkeysib-")) {
            throw new IllegalStateException("Brevo API key (xkeysib-...) not configured");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("api-key", apiKey);

        Map<String, Object> body = Map.of(
                "sender", Map.of("name", fromName, "email", fromEmail),
                "to", List.of(Map.of("email", to)),
                "subject", subject,
                "textContent", text,
                "htmlContent", html != null ? html : "<p>" + text.replace("\n", "<br>") + "</p>"
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        restTemplate.postForEntity("https://api.brevo.com/v3/smtp/email", request, String.class);
        log.info("Email sent via Brevo API to {}", to);
    }

    private void sendViaSmtp(String to, String subject, String text, String html,
                             String host, String username, String password) throws Exception {
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("SMTP password not configured for " + host);
        }

        JavaMailSender sender = createMailSender(host, username, password);
        MimeMessage message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(new InternetAddress(username, fromName));
        helper.setTo(to);
        helper.setSubject(subject);
        if (html != null) {
            helper.setText(text, html);
            if (html.contains("cid:disasterLogo")) {
                attachDisasterLogo(helper);
            }
        } else {
            helper.setText(text, false);
        }
        sender.send(message);
        log.info("Email sent via SMTP ({}) to {}", host, to);
    }

    private void attachDisasterLogo(MimeMessageHelper helper) {
        try {
            org.springframework.core.io.ClassPathResource classPathResource = 
                    new org.springframework.core.io.ClassPathResource("static/images/disaster_logo.jpg");
            if (classPathResource.exists()) {
                helper.addInline("disasterLogo", classPathResource, "image/jpeg");
                return;
            }
            java.io.File file = new java.io.File("src/main/resources/static/images/disaster_logo.jpg");
            if (!file.exists()) {
                file = new java.io.File("backend/src/main/resources/static/images/disaster_logo.jpg");
            }
            if (file.exists()) {
                helper.addInline("disasterLogo", new org.springframework.core.io.FileSystemResource(file), "image/jpeg");
            }
        } catch (Exception e) {
            log.warn("Could not attach inline disaster logo: {}", e.getMessage());
        }
    }

    private JavaMailSender createMailSender(String host, String username, String password) {
        org.springframework.mail.javamail.JavaMailSenderImpl impl = new org.springframework.mail.javamail.JavaMailSenderImpl();
        impl.setHost(host);
        impl.setPort(587);
        impl.setUsername(username);
        impl.setPassword(password);
        java.util.Properties props = impl.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");
        props.put("mail.smtp.ssl.trust", host);
        props.put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3");
        props.put("mail.debug", "false");
        return impl;
    }

    private boolean useBrevoApi() {
        String key = (brevoApiKey != null && !brevoApiKey.isBlank()) ? brevoApiKey : mailPassword;
        return "brevo-api".equalsIgnoreCase(mailProvider)
                || ("auto".equalsIgnoreCase(mailProvider) && key != null && key.startsWith("xkeysib-"));
    }

    private boolean useGmail() {
        return "gmail".equalsIgnoreCase(mailProvider);
    }

    @Value("${GMAIL_APP_PASSWORD:}")
    private String gmailAppPassword;
    @Value("${SOS_ALERT_EMAIL:}")
    private String sosAuthorityEmail;

    /**
     * Send SOS alert to a designated authority email (e.g., central emergency coordination).
     */
    public void sendSosAlertToAuthority(String to, com.disaster.model.RescueRequest request) {
        // Reuse the same email content as organisation alerts.
        sendSosAlertToOrganisation(to, request);
    }

        public String getSosAuthorityEmail() {
        return sosAuthorityEmail;
    }
    @Value("${spring.mail.brevo-api-key:${BREVO_API_KEY:}}")
    private String brevoApiKey;

    private boolean hasGmailCredentials() {
        return (gmailAppPassword != null && !gmailAppPassword.isBlank())
                || (useGmail() && mailPassword != null && !mailPassword.isBlank());
    }

    private String getGmailPassword() {
        String pw = (gmailAppPassword != null && !gmailAppPassword.isBlank()) ? gmailAppPassword : mailPassword;
        if (pw != null) {
            return pw.replace("\"", "").replace("'", "").trim();
        }
        return "";
    }

    private String resolveSmtpUsername() {
        return fromEmail;
    }

    public boolean isConfigured() {
        return fromEmail != null && !fromEmail.isBlank();
    }

    public static class EmailDeliveryException extends Exception {
        public EmailDeliveryException(String message) {
            super(message);
        }

        public EmailDeliveryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
