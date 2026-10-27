package kz.edu.biletflow.backend.services;

import kz.edu.biletflow.backend.dtos.AssignStaffRequest;
import kz.edu.biletflow.backend.dtos.StaffResponse;

import java.util.List;

public interface StaffService {
    StaffResponse assignStaff(Long eventId, Long requesterId, boolean isPlatformAdmin, AssignStaffRequest request);

    List<StaffResponse> getStaff(Long eventId, Long requesterId, boolean isPlatformAdmin);

    void removeStaff(Long eventId, Long userId, Long requesterId, boolean isPlatformAdmin);
}
