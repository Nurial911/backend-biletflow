package kz.edu.biletflow.backend.entities;

public enum PaymentSessionStatus {
    OPEN,        // the shopper can pay on the bank's page
    CAPTURING,   // we accepted the payment and asked the bank to take the held money
    PAID,        // money taken, tickets issued
    RELEASE_DUE, // the bank holds money we can't accept, so it must go back
    RELEASED,    // the money went back
    ABANDONED    // the page expired without a payment
}
