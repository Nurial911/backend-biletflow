package kz.edu.biletflow.backend.services;

import kz.edu.biletflow.backend.dtos.CreateTicketTypeRequest;
import kz.edu.biletflow.backend.dtos.TicketTypeResponse;
import kz.edu.biletflow.backend.dtos.UpdateTicketTypeRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TicketTypeService {
    TicketTypeResponse create(Long organizerId, Long eventId, CreateTicketTypeRequest request);
    TicketTypeResponse update(Long organizerId, Long ticketTypeId, UpdateTicketTypeRequest request);
    Page<TicketTypeResponse> list(Long eventId, Pageable pageable);
}
