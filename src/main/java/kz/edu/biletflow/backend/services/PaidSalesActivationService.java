package kz.edu.biletflow.backend.services;

import jakarta.annotation.Nullable;
import kz.edu.biletflow.backend.dtos.PaidSalesActivationResponse;
import kz.edu.biletflow.backend.entities.PaidSalesStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PaidSalesActivationService {
    PaidSalesActivationResponse getActivation(Long eventId, @Nullable Long requesterId, boolean isPlatformAdmin);

    PaidSalesActivationResponse payFee(Long organizerId, Long eventId);

    PaidSalesActivationResponse acceptTerms(Long organizerId, Long eventId);

    PaidSalesActivationResponse activate(Long organizerId, Long eventId);

    PaidSalesActivationResponse suspend(Long adminId, Long eventId, @Nullable String reason);

    PaidSalesActivationResponse unsuspend(Long adminId, Long eventId);

    Page<PaidSalesActivationResponse> getActivations(@Nullable PaidSalesStatus status, Pageable pageable);
}
