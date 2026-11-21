package com.bankservices.bankservices_api.notification;

import com.bankservices.bankservices_api.service.EmailService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the e-mail layer: {@link EmailService} (password reset mail),
 * {@link NotificationService} (generic mail) and
 * {@link NotificationEventListener} (after-commit event handling).
 * JavaMailSender is mocked, so no SMTP server is contacted.
 */
@ExtendWith(MockitoExtension.class)
class NotificationAndEmailTest {

    @Mock private JavaMailSender mailSender;
    @Mock private NotificationService notificationService;

    @Test
    void emailService_sendPasswordResetEmail_sendsMailContainingResetLink() {
        EmailService emailService = new EmailService(mailSender);
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);

        emailService.sendPasswordResetEmail("alice@example.test", "http://frontend.test/reset-password?token=abc");

        verify(mailSender).send(captor.capture());
        SimpleMailMessage mail = captor.getValue();
        assertThat(mail.getTo()).containsExactly("alice@example.test");
        assertThat(mail.getSubject()).contains("Password Reset");
        assertThat(mail.getText()).contains("http://frontend.test/reset-password?token=abc")
                .contains("15 minutes");
    }

    @Test
    void notificationService_sendEmail_sendsMailWithGivenFields() {
        NotificationService service = new NotificationService(mailSender);
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);

        service.sendEmail("bob@example.test", "Subject", "Body");

        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getTo()).containsExactly("bob@example.test");
        assertThat(captor.getValue().getSubject()).isEqualTo("Subject");
        assertThat(captor.getValue().getText()).isEqualTo("Body");
    }

    @Test
    void listener_withValidEvent_delegatesToNotificationService() {
        NotificationEventListener listener = new NotificationEventListener(notificationService);

        listener.handleNotification(new AccountNotificationEvent("bob@example.test", "S", "M"));

        verify(notificationService).sendEmail("bob@example.test", "S", "M");
    }

    @Test
    void listener_withBlankOrNullEmail_skipsSending() {
        NotificationEventListener listener = new NotificationEventListener(notificationService);

        listener.handleNotification(new AccountNotificationEvent(" ", "S", "M"));
        listener.handleNotification(new AccountNotificationEvent(null, "S", "M"));

        verifyNoInteractions(notificationService);
    }

    @Test
    void listener_whenMailServerFails_swallowsExceptionSoCommittedOperationIsNotAffected() {
        NotificationEventListener listener = new NotificationEventListener(notificationService);
        doThrow(new MailSendException("SMTP down"))
                .when(notificationService).sendEmail(anyString(), anyString(), anyString());

        assertThatCode(() -> listener.handleNotification(
                new AccountNotificationEvent("bob@example.test", "S", "M")))
                .doesNotThrowAnyException();
    }

    @Test
    void emailService_propagatesMailFailureToCaller() {
        EmailService emailService = new EmailService(mailSender);
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(SimpleMailMessage.class));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        emailService.sendPasswordResetEmail("alice@example.test", "link"))
                .isInstanceOf(MailSendException.class);
    }
}
