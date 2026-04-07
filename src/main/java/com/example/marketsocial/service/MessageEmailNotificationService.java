package com.example.marketsocial.service;

import com.example.marketsocial.model.Message;
import com.example.marketsocial.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class MessageEmailNotificationService {
    private static final Logger log = LoggerFactory.getLogger(MessageEmailNotificationService.class);

    private final JavaMailSender mailSender;
    private final boolean enabled;
    private final String fromAddress;

    public MessageEmailNotificationService(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${app.notifications.email.enabled:false}") boolean enabled,
            @Value("${app.notifications.email.from:}") String fromAddress
    ) {
        this.mailSender = mailSenderProvider.getIfAvailable();
        this.enabled = enabled;
        this.fromAddress = fromAddress == null ? "" : fromAddress.trim();
    }

    public void sendNewMessageNotification(User receiver, User sender, Message message) {
        if (!enabled || mailSender == null || receiver == null || sender == null || message == null) {
            return;
        }
        if (!receiver.canReceiveEmailNotifications()) {
            return;
        }

        try {
            SimpleMailMessage email = new SimpleMailMessage();
            if (!fromAddress.isBlank()) {
                email.setFrom(fromAddress);
            }
            email.setTo(receiver.getEmail());
            email.setSubject("New message on MarketSocial");
            email.setText(
                    "Hi " + (receiver.getDisplayName() == null || receiver.getDisplayName().isBlank() ? receiver.getUsername() : receiver.getDisplayName()) + ",\n\n" +
                            "You have a new message from " + sender.getDisplayName() + " (@" + sender.getUsername() + ").\n\n" +
                            "Message preview:\n" +
                            message.getContent() + "\n\n" +
                            "Sign in to MarketSocial to reply."
            );
            mailSender.send(email);
        } catch (Exception exception) {
            log.warn("Could not send new-message email to {}", receiver.getUsername(), exception);
        }
    }
}
