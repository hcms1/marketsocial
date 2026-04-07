package com.example.marketsocial;

import com.example.marketsocial.model.Message;
import com.example.marketsocial.model.User;
import com.example.marketsocial.service.MessageEmailNotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageEmailNotificationServiceTests {

    @Test
    void sendsEmailWhenEnabledAndRecipientHasOptedIn() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mailSender);

        MessageEmailNotificationService service = new MessageEmailNotificationService(
                provider,
                true,
                "alerts@marketsocial.test"
        );

        User sender = user(1L, "sender", "Sender Name", null, false);
        User receiver = user(2L, "receiver", "Receiver Name", "receiver@example.com", true);
        Message message = new Message(99L, sender, receiver, "Fresh stock just landed", LocalDateTime.now(), null);

        service.sendNewMessageNotification(receiver, sender, message);

        org.mockito.ArgumentCaptor<SimpleMailMessage> emailCaptor = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(emailCaptor.capture());
        SimpleMailMessage email = emailCaptor.getValue();
        assertThat(email.getFrom()).isEqualTo("alerts@marketsocial.test");
        assertThat(email.getTo()).containsExactly("receiver@example.com");
        assertThat(email.getSubject()).isEqualTo("New message on MarketSocial");
        assertThat(email.getText()).contains("Fresh stock just landed");
        assertThat(email.getText()).contains("@sender");
    }

    @Test
    void skipsEmailWhenRecipientHasNotOptedIn() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mailSender);

        MessageEmailNotificationService service = new MessageEmailNotificationService(
                provider,
                true,
                "alerts@marketsocial.test"
        );

        User sender = user(1L, "sender", "Sender Name", null, false);
        User receiver = user(2L, "receiver", "Receiver Name", "receiver@example.com", false);
        Message message = new Message(99L, sender, receiver, "Fresh stock just landed", LocalDateTime.now(), null);

        service.sendNewMessageNotification(receiver, sender, message);

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    private User user(Long id, String username, String displayName, String email, boolean emailNotificationsEnabled) {
        return new User(
                id,
                username,
                "encoded-password",
                User.ROLE_USER,
                displayName,
                "",
                "",
                email,
                emailNotificationsEnabled
        );
    }
}
