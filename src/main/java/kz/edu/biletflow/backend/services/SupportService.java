package kz.edu.biletflow.backend.services;

import kz.edu.biletflow.backend.dtos.*;

public interface SupportService {
    SupportCaseResponse create(Long userId, CreateSupportCaseRequest request);
    SupportCaseResponse addMessage(Long userId, Long caseId, SupportMessageRequest request);
    SupportCaseResponse updateStatus(Long userId, Long caseId, SupportStatusRequest request);
}
