package kz.edu.biletflow.backend.services;

import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import kz.edu.biletflow.backend.entities.Ticket;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;

@Service
@RequiredArgsConstructor
public class PdfTicketService {
    public byte[] createPdf(Ticket ticket) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            Document document = new Document();
            PdfWriter.getInstance(document, output);
            document.open();
            document.add(new Paragraph("BiletFlow Ticket"));
            document.add(new Paragraph("Ticket: " + ticket.getTicketIdentifier()));
            document.add(new Paragraph("Event ID: " + ticket.getEvent().getId()));
            document.add(new Paragraph("Status: " + ticket.getStatus()));
            document.add(new Paragraph("QR payload: " + ticket.getQrCode()));
            document.close();
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("Could not generate ticket PDF", exception);
        }
    }
}
