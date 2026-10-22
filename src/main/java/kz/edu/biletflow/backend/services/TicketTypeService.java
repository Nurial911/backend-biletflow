package kz.edu.biletflow.backend.services;

import kz.edu.biletflow.backend.dtos.CreateTicketTypeRequest;
import kz.edu.biletflow.backend.dtos.TicketTypeResponse;
import kz.edu.biletflow.backend.dtos.UpdateTicketTypeRequest;

import java.util.List;

public interface TicketTypeService {
    TicketTypeResponse createTicketType(Long organizerId, Long eventId, CreateTicketTypeRequest request);

    List<TicketTypeResponse> getTicketTypesForEvent(Long eventId, Long requesterId, boolean isPlatformAdmin);

    TicketTypeResponse updateTicketType(Long organizerId, Long ticketTypeId, UpdateTicketTypeRequest request);

    TicketTypeResponse setHidden(Long organizerId, Long ticketTypeId, boolean hidden);
}