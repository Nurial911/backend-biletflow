package kz.edu.biletflow.backend.controllers.impl;

import kz.edu.biletflow.backend.controllers.SupportController;
import kz.edu.biletflow.backend.dtos.*;
import kz.edu.biletflow.backend.entities.SupportCaseStatus;
import kz.edu.biletflow.backend.security.UserPrincipal;
import kz.edu.biletflow.backend.services.SupportService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequiredArgsConstructor
public class SupportControllerImpl implements SupportController {
    private final SupportService supportService;

    @Override
    public ResponseEntity<SupportCaseDetailResponse> openCase(UserPrincipal currentUser, OpenSupportCaseRequest request) {
        SupportCaseDetailResponse created = supportService.openCase(currentUser.getId(), isAdmin(currentUser), request);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getSupportCase().getId())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @Override
    public ResponseEntity<Page<SupportCaseResponse>> getCases(UserPrincipal currentUser, SupportCaseStatus status,
                                                              Boolean escalated, Pageable pageable) {
        return ResponseEntity.ok(supportService.getCases(currentUser.getId(), isAdmin(currentUser), status, escalated, pageable));
    }

    @Override
    public ResponseEntity<SupportCaseDetailResponse> getCase(UserPrincipal currentUser, Long caseId) {
        return ResponseEntity.ok(supportService.getCase(caseId, currentUser.getId(), isAdmin(currentUser)));
    }

    @Override
    public ResponseEntity<SupportCaseDetailResponse> addMessage(UserPrincipal currentUser, Long caseId, SupportMessageRequest request) {
        return ResponseEntity.ok(supportService.addMessage(caseId, currentUser.getId(), isAdmin(currentUser), request.getBody()));
    }

    @Override
    public ResponseEntity<SupportCaseDetailResponse> assign(UserPrincipal currentUser, Long caseId, AssignSupportCaseRequest request) {
        return ResponseEntity.ok(supportService.assign(caseId, currentUser.getId(), isAdmin(currentUser), request.getEmail()));
    }

    @Override
    public ResponseEntity<SupportCaseDetailResponse> changeStatus(UserPrincipal currentUser, Long caseId, ChangeSupportCaseStatusRequest request) {
        return ResponseEntity.ok(supportService.changeStatus(caseId, currentUser.getId(), isAdmin(currentUser), request.getStatus()));
    }

    @Override
    public ResponseEntity<SupportCaseDetailResponse> escalate(UserPrincipal currentUser, Long caseId) {
        return ResponseEntity.ok(supportService.escalate(caseId, currentUser.getId(), isAdmin(currentUser)));
    }

    private static boolean isAdmin(UserPrincipal currentUser) {
        return currentUser.hasRole("PLATFORM_ADMIN");
    }
}
