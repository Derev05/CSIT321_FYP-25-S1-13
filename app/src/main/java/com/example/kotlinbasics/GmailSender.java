package com.example.kotlinbasics;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.Properties;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

public class GmailSender {
    private static final String TAG = "GmailSender";

    // 🔐 Replace with your actual Gmail + App Password
    private static final String EMAIL_SENDER = "syzwanshukor@gmail.com";
    private static final String EMAIL_PASSWORD = "oyhw krfg kqgd rvac";

    // 📧 Display name (shows in inbox)
    private static final String SENDER_NAME = "BioAuth OTP Service";

    private static final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    // 🔢 Generate 6-digit OTP
    public static String generateOTP() {
        Random random = new Random();
        return String.format("%06d", random.nextInt(999999));
    }

    // ✉ Send Email Async
    public static void sendEmailAsync(String recipientEmail, String subject, String messageBody, EmailCallback callback) {
        executorService.execute(() -> {
            boolean success = sendEmail(recipientEmail, subject, messageBody);
            mainHandler.post(() -> {
                if (callback != null) {
                    callback.onEmailSent(success);
                }
            });
        });
    }

    // ✉ Send Email (Sync)
    private static boolean sendEmail(String recipientEmail, String subject, String messageBody) {
        try {
            Log.d(TAG, "📧 Preparing to send email to: " + recipientEmail);

            Properties props = new Properties();
            props.put("mail.smtp.host", "smtp.gmail.com");
            props.put("mail.smtp.port", "587"); // TLS port
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");

            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(EMAIL_SENDER, EMAIL_PASSWORD);
                }
            });

            Message message = new MimeMessage(session);

            // ✅ Use sender name with email
            message.setFrom(new InternetAddress(EMAIL_SENDER, SENDER_NAME));

            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipientEmail));
            message.setSubject(subject);
            message.setText(messageBody);

            Transport.send(message);

            Log.d(TAG, "✅ Email successfully sent to: " + recipientEmail);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to send email: " + e.getMessage(), e);
            return false;
        }
    }

    // ✅ Callback
    public interface EmailCallback {
        void onEmailSent(boolean success);
    }
}
