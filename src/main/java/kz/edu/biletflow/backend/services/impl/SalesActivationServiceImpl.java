package kz.edu.biletflow.backend.services.impl;

import kz.edu.biletflow.backend.dtos.ActivationRequest;
import kz.edu.biletflow.backend.dtos.ActivationResponse;
import kz.edu.biletflow.backend.entities.*;
import kz.edu.biletflow.backend.exception.ForbiddenOperationException;
import kz.edu.biletflow.backend.exception.ResourceNotFoundException;
import kz.edu.biletflow.backend.repositories.*;
import kz.edu.biletflow.backend.services.SalesActivationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SalesActivationServiceImpl implements SalesActivationService {
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final PayoutAccountRepository payoutAccountRepository;
    private final PaidSalesActivationRepository activationRepository;

    @Override
    @Transactional
    public ActivationResponse activate(Long organizerId, Long eventId, ActivationRequest request) {
        User organizer = userRepository.findById(organizerId)
                .orElseThrow(() -> new ResourceNotFoundException("Organizer not found with id: " + organizerId));
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + eventId));
        if (!event.getOrganizer().getId().equals(organizerId)) {
            throw new ForbiddenOperationException("You are not authorized to activate sales for this event");
        }
        PayoutAccount payoutAccount = new PayoutAccount();
        payoutAccount.setOrganizer(organizer);
        payoutAccount.setAccountDetails(request.getPayoutAccountDetails());
        payoutAccount.setVerificationStatus(VerificationStatus.VERIFIED);
        payoutAccount = payoutAccountRepository.save(payoutAccount);

        PaidSalesActivation activation = activationRepository.findByEventId(eventId).orElseGet(PaidSalesActivation::new);
        activation.setEvent(event);
        activation.setPayoutAccount(payoutAccount);
        activation.setActivationFee(java.math.BigDecimal.ZERO);
        activation.setStatus(ActivationStatus.ACTIVE);
        activation.setActivatedAt(java.time.LocalDateTime.now());
        activation = activationRepository.save(activation);

        ActivationResponse response = new ActivationResponse();
        response.setEventId(eventId);
        response.setStatus(activation.getStatus());
        response.setPayoutAccountId(payoutAccount.getId());
        return response;
    }
}
