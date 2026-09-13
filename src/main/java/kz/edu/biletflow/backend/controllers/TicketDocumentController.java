package kz.edu.biletflow.backend.controllers;

import kz.edu.biletflow.backend.entities.Ticket;
import kz.edu.biletflow.backend.exception.ResourceNotFoundException;
import kz.edu.biletflow.backend.repositories.TicketRepository;
import kz.edu.biletflow.backend.services.PdfTicketService;
import kz.edu.biletflow.backend.services.QrCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketDocumentController {
    private final TicketRepository ticketRepository;
    private final QrCodeService qrCodeService;
    private final PdfTicketService pdfTicketService;

    @GetMapping(value = "/{ticketIdentifier}/qr", produces = MediaType.IMAGE_PNG_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> qr(@PathVariable String ticketIdentifier) {
        Ticket ticket = find(ticketIdentifier);
        return ResponseEntity.ok(qrCodeService.createPng(ticket.getQrCode()));
    }

    @GetMapping(value = "/{ticketIdentifier}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> pdf(@PathVariable String ticketIdentifier) {
        return ResponseEntity.ok(pdfTicketService.createPdf(find(ticketIdentifier)));
    }

    private Ticket find(String identifier) {
        return ticketRepository.findByTicketIdentifier(identifier)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));
    }
}
