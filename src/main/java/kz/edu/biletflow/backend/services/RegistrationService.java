package kz.edu.biletflow.backend.services;

import kz.edu.biletflow.backend.dtos.FreeRegistrationRequest;
import kz.edu.biletflow.backend.dtos.OrderResponse;

public interface RegistrationService {
    OrderResponse registerFree(Long userId, Long eventId, FreeRegistrationRequest request);
}
