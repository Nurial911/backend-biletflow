package kz.edu.biletflow.backend.controllers;

import jakarta.validation.Valid;
import kz.edu.biletflow.backend.dtos.*;
import kz.edu.biletflow.backend.entities.SupportCaseStatus;
import kz.edu.biletflow.backend.security.UserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

// Roles alone don't decide access here: the service checks the user's relation to each case.
@RequestMapping("/api/support/cases")
public interface SupportController {
    @PostMapping
    ResponseEntity<SupportCaseDetailResponse> openCase(@AuthenticationPrincipal UserPrincipal currentUser,
                                                       @Valid @RequestBody OpenSupportCaseRequest request);

    // Cases the user opened or may handle; a platform admin sees all of them.
    @GetMapping
    ResponseEntity<Page<SupportCaseResponse>> getCases(@AuthenticationPrincipal UserPrincipal currentUser,
                                                       @RequestParam(required = false) SupportCaseStatus status,
                                                       @RequestParam(required = false) Boolean escalated,
                                                       @PageableDefault(sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable);

    @GetMapping("/{caseId}")
    ResponseEntity<SupportCaseDetailResponse> getCase(@AuthenticationPrincipal UserPrincipal currentUser,
                                                      @PathVariable Long caseId);

    @PostMapping("/{caseId}/messages")
    ResponseEntity<SupportCaseDetailResponse> addMessage(@AuthenticationPrincipal UserPrincipal currentUser,
                                                         @PathVariable Long caseId,
                                                         @Valid @RequestBody SupportMessageRequest request);

    @PostMapping("/{caseId}/assign")
    ResponseEntity<SupportCaseDetailResponse> assign(@AuthenticationPrincipal UserPrincipal currentUser,
                                                     @PathVariable Long caseId,
                                                     @Valid @RequestBody AssignSupportCaseRequest request);

    @PostMapping("/{caseId}/status")
    ResponseEntity<SupportCaseDetailResponse> changeStatus(@AuthenticationPrincipal UserPrincipal currentUser,
                                                           @PathVariable Long caseId,
                                                           @Valid @RequestBody ChangeSupportCaseStatusRequest request);

    @PostMapping("/{caseId}/escalate")
    ResponseEntity<SupportCaseDetailResponse> escalate(@AuthenticationPrincipal UserPrincipal currentUser,
                                                       @PathVariable Long caseId);
}
