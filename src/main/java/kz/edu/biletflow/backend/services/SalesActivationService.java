package kz.edu.biletflow.backend.services;

import kz.edu.biletflow.backend.dtos.ActivationRequest;
import kz.edu.biletflow.backend.dtos.ActivationResponse;

public interface SalesActivationService {
    ActivationResponse activate(Long organizerId, Long eventId, ActivationRequest request);
}
