
package com.bankservices.bankservices_api.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class NotificationEventListener {

    private static final Logger logger =
            LoggerFactory.getLogger(NotificationEventListener.class);

    private final NotificationService notificationService;

    public NotificationEventListener(
            NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleNotification(AccountNotificationEvent event) {

        try {
            if (event.email() == null || event.email().isBlank()) {
                logger.warn("Notification skipped: client email is missing.");
                return;
            }

            notificationService.sendEmail(
                    event.email(),
                    event.subject(),
                    event.message()
            );

            logger.info("Notification email sent to {}", maskEmail(event.email()));

        } catch (MailException exception) {
            logger.error(
                    "Failed to send notification email to {}",
                    maskEmail(event.email()),
                    exception
            );
        }
    }

    /** "alice.martin@example.com" -> "a***@example.com": enough to diagnose, not to identify. */
    static String maskEmail(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
