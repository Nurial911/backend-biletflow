package kz.edu.biletflow.backend.services;

import jakarta.annotation.Nullable;
import kz.edu.biletflow.backend.dtos.OpenSupportCaseRequest;
import kz.edu.biletflow.backend.dtos.SupportCaseDetailResponse;
import kz.edu.biletflow.backend.dtos.SupportCaseResponse;
import kz.edu.biletflow.backend.entities.SupportCaseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SupportService {
    SupportCaseDetailResponse openCase(Long userId, boolean isPlatformAdmin, OpenSupportCaseRequest request);

    Page<SupportCaseResponse> getCases(Long userId, boolean isPlatformAdmin, @Nullable SupportCaseStatus status,
                                       @Nullable Boolean escalated, Pageable pageable);

    SupportCaseDetailResponse getCase(Long caseId, Long userId, boolean isPlatformAdmin);

    SupportCaseDetailResponse addMessage(Long caseId, Long userId, boolean isPlatformAdmin, String body);

    SupportCaseDetailResponse assign(Long caseId, Long userId, boolean isPlatformAdmin, String assigneeEmail);

    SupportCaseDetailResponse changeStatus(Long caseId, Long userId, boolean isPlatformAdmin, SupportCaseStatus status);

    SupportCaseDetailResponse escalate(Long caseId, Long userId, boolean isPlatformAdmin);
}
