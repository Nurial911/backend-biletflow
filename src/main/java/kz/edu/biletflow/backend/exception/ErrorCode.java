package kz.edu.biletflow.backend.exception;

/**
 * Machine-readable reason in every {@link ErrorResponse}, so clients can react without parsing the
 * English {@code message}. The HTTP status stays the main signal; the code tells cases with the same
 * status apart (a suspended account and a missing permission are both 403).
 * <p>
 * Codes are part of the API contract: add new ones freely, but never rename or remove one.
 */
public enum ErrorCode {

    // 400: the request itself is wrong
    VALIDATION_FAILED,          // a field failed @Valid checks; the message names the fields
    BAD_REQUEST,                // malformed JSON, wrong parameter type, unknown sort field, broken multipart
    BUSINESS_RULE_VIOLATION,    // any other domain rule; the message explains which

    // 400: buying tickets
    REGISTRATION_NOT_OPEN,      // the event's registration window hasn't opened yet
    REGISTRATION_CLOSED,        // the registration window has closed
    EVENT_ALREADY_STARTED,
    EVENT_UNAVAILABLE,          // the event was unpublished, cancelled or suspended after the order was made
    EVENT_SUSPENDED,            // a platform admin suspended the event (only its organizer/admins see this)
    PAID_SALES_NOT_ACTIVE,      // paid tickets aren't on sale yet (activation checklist not finished)
    PAID_SALES_SUSPENDED,       // a platform admin stopped paid sales
    TICKET_SALES_NOT_STARTED,   // the ticket type's own sales window hasn't started
    TICKET_SALES_ENDED,         // the ticket type's own sales window has ended
    MAX_PER_ORDER_EXCEEDED,
    NOT_ENOUGH_TICKETS,         // fewer tickets left than requested; the message says how many
    ORDER_NOT_PENDING,          // the order was already paid, cancelled, expired or refunded
    ORDER_EXPIRED,              // the reservation ran out; create a new order

    // 400: promo codes
    PROMO_CODE_INVALID,         // no such code or token for this event
    PROMO_CODE_DISABLED,
    PROMO_CODE_NOT_STARTED,
    PROMO_CODE_EXPIRED,
    PROMO_CODE_LIMIT_REACHED,   // every redemption is used (paid or held by an unpaid order)
    PROMO_CODE_NOT_APPLICABLE,  // the code doesn't cover any of the selected ticket types

    // 401
    UNAUTHENTICATED,            // no token, or the token is invalid or expired: sign in again
    BAD_CREDENTIALS,            // wrong email or password on login

    // 402
    PAYMENT_DECLINED,

    // 403
    ACCESS_DENIED,              // signed in, but this role or user may not do this
    ACCOUNT_SUSPENDED,          // the account is suspended: on login and on every request with its token

    // 404
    NOT_FOUND,                  // also for things the requester may not see (hidden-existence rule)

    // 405
    METHOD_NOT_ALLOWED,

    // 409
    CONFLICT,                   // generic: something already exists
    DATA_CONFLICT,              // a database uniqueness rule caught a concurrent duplicate
    EMAIL_ALREADY_REGISTERED,
    ORGANIZER_PROFILE_EXISTS,
    STAFF_ALREADY_ASSIGNED,
    EVENT_ALREADY_REPORTED,     // the user's earlier report of this event is still waiting for review
    SEAT_UNAVAILABLE,           // someone else took the seat, or it was blocked: refresh the seat map

    // 413
    FILE_TOO_LARGE,

    // 502
    PAYMENT_PROVIDER_UNAVAILABLE,

    // 500
    INTERNAL_ERROR
}
