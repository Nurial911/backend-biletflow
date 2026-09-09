package kz.edu.biletflow.backend.services;

import com.sun.jdi.request.EventRequest;
import kz.edu.biletflow.backend.dtos.CreateEventRequest;
import kz.edu.biletflow.backend.dtos.EventResponse;

public interface EventService {
    EventResponse createEvent(Long organizerId, CreateEventRequest eventRequest);
}
