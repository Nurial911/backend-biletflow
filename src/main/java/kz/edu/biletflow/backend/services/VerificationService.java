package kz.edu.biletflow.backend.services;

import kz.edu.biletflow.backend.dtos.TicketResponse;

public interface VerificationService {
    TicketResponse validate(String ticketIdentifier, String qrPayload);
    TicketResponse checkIn(Long adminId, String ticketIdentifier, String qrPayload);
    TicketResponse undoCheckIn(Long adminId, String ticketIdentifier);
}
