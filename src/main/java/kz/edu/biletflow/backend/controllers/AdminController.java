package kz.edu.biletflow.backend.controllers;

import kz.edu.biletflow.backend.entities.Event;
import kz.edu.biletflow.backend.entities.PaidSalesActivation;
import kz.edu.biletflow.backend.entities.User;
import kz.edu.biletflow.backend.repositories.EventRepository;
import kz.edu.biletflow.backend.repositories.PaidSalesActivationRepository;
import kz.edu.biletflow.backend.repositories.UserRepository;
import kz.edu.biletflow.backend.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class AdminController {
    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final PaidSalesActivationRepository activationRepository;

    @GetMapping("/users")
    public Page<User> users(Pageable pageable) { return userRepository.findAll(pageable); }

    @PatchMapping("/users/{id}/suspension")
    public ResponseEntity<Void> suspend(@PathVariable Long id, @RequestParam boolean suspended) {
        User user = userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setSuspended(suspended);
        userRepository.save(user);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/events")
    public Page<Event> events(Pageable pageable) { return eventRepository.findAll(pageable); }

    @GetMapping("/sales-activations")
    public Page<PaidSalesActivation> activations(Pageable pageable) { return activationRepository.findAll(pageable); }
}
