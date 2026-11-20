package kz.edu.biletflow.backend.entities;

// Stored as plain text (Notification.type), so adding a value needs no constraint change.
public enum NotificationType {
    ORDER_CONFIRMED,
    PAYMENT_FAILED,
    REFUND_COMPLETED,
    REGISTRATION_CANCELLED,
    EVENT_UPDATED,
    EVENT_CANCELLED,
    ORGANIZER_VERIFICATION,
    PAID_SALES_STATUS,
    SUPPORT_MESSAGE,
    SUPPORT_ASSIGNED,
    SUPPORT_STATUS,
    // Only the email reaches a suspended user: they can't sign in to read in-app notifications.
    ACCOUNT_SUSPENDED,
    ACCOUNT_REINSTATED,
    EVENT_REPORT_REVIEWED
}
