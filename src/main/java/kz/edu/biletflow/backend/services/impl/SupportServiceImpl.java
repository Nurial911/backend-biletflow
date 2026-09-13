package kz.edu.biletflow.backend.services.impl;

import kz.edu.biletflow.backend.dtos.*;
import kz.edu.biletflow.backend.entities.*;
import kz.edu.biletflow.backend.exception.ForbiddenOperationException;
import kz.edu.biletflow.backend.exception.ResourceNotFoundException;
import kz.edu.biletflow.backend.repositories.*;
import kz.edu.biletflow.backend.services.SupportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SupportServiceImpl implements SupportService {
    private final UserRepository userRepository;
    private final SupportCaseRepository caseRepository;
    private final SupportMessageRepository messageRepository;
    private final EventRepository eventRepository;
    private final OrderRepository orderRepository;
    private final TicketRepository ticketRepository;

    @Override
    @Transactional
    public SupportCaseResponse create(Long userId, CreateSupportCaseRequest request) {
        User user = user(userId);
        SupportCase supportCase = new SupportCase();
        supportCase.setUser(user);
        supportCase.setCaseType(request.getCaseType());
        supportCase.setIssueCategory(request.getIssueCategory());
        if (request.getEventId() != null) supportCase.setEvent(eventRepository.findById(request.getEventId()).orElseThrow());
        if (request.getOrderId() != null) supportCase.setOrder(orderRepository.findById(request.getOrderId()).orElseThrow());
        if (request.getTicketId() != null) supportCase.setTicket(ticketRepository.findById(request.getTicketId()).orElseThrow());
        return response(caseRepository.save(supportCase));
    }

    @Override
    @Transactional
    public SupportCaseResponse addMessage(Long userId, Long caseId, SupportMessageRequest request) {
        SupportCase supportCase = ownedOrStaff(userId, caseId);
        SupportMessage message = new SupportMessage();
        message.setSupportCase(supportCase);
        message.setSender(user(userId));
        message.setMessageContent(request.getMessageContent());
        messageRepository.save(message);
        return response(supportCase);
    }

    @Override
    @Transactional
    public SupportCaseResponse updateStatus(Long userId, Long caseId, SupportStatusRequest request) {
        SupportCase supportCase = ownedOrStaff(userId, caseId);
        supportCase.setStatus(request.getStatus());
        return response(caseRepository.save(supportCase));
    }

    private SupportCase ownedOrStaff(Long userId, Long caseId) {
        SupportCase supportCase = caseRepository.findById(caseId).orElseThrow(() -> new ResourceNotFoundException("Support case not found"));
        User user = user(userId);
        if (!supportCase.getUser().getId().equals(userId) && user.getRole() != User.Role.EVENT_ADMIN && user.getRole() != User.Role.PLATFORM_ADMIN) {
            throw new ForbiddenOperationException("You are not authorized to access this case");
        }
        return supportCase;
    }

    private User user(Long id) { return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found")); }

    private SupportCaseResponse response(SupportCase supportCase) {
        SupportCaseResponse response = new SupportCaseResponse();
        response.setId(supportCase.getId());
        response.setCaseType(supportCase.getCaseType());
        response.setStatus(supportCase.getStatus());
        response.setIssueCategory(supportCase.getIssueCategory());
        if (supportCase.getEvent() != null) response.setEventId(supportCase.getEvent().getId());
        if (supportCase.getOrder() != null) response.setOrderId(supportCase.getOrder().getId());
        if (supportCase.getTicket() != null) response.setTicketId(supportCase.getTicket().getId());
        return response;
    }
}
