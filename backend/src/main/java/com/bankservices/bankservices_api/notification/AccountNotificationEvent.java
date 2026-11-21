
package com.bankservices.bankservices_api.notification;

public record AccountNotificationEvent(
        String email,
        String subject,
        String message
) {
}
