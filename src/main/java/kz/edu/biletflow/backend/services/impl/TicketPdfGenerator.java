package kz.edu.biletflow.backend.services.impl;

import com.google.zxing.common.BitMatrix;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import kz.edu.biletflow.backend.entities.Event;
import kz.edu.biletflow.backend.entities.Ticket;
import kz.edu.biletflow.backend.entities.TicketStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * A4 print-optimized tickets (SRS 4.7), one per page. Everything is pure black on white, so a
 * grayscale printer loses nothing. No payment details are printed. Call inside a transaction:
 * the ticket's relations are lazy.
 */
@Component
@RequiredArgsConstructor
public class TicketPdfGenerator {
    // In points. About 7.8 cm of QR, at a fixed spot so long titles can never run into it.
    public static final float QR_SIZE = 220f;
    public static final float QR_BOTTOM = 130f;

    private static final float MARGIN = 56f;
    private static final float PAGE_WIDTH = PageSize.A4.getWidth();
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm");

    private static final BaseFont REGULAR = embeddedFont("LiberationSans-Regular.ttf");
    private static final BaseFont BOLD = embeddedFont("LiberationSans-Bold.ttf");

    private final QrCodeGenerator qrCodeGenerator;

    public byte[] generate(List<Ticket> tickets) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, MARGIN, MARGIN, MARGIN, MARGIN);
        PdfWriter writer = PdfWriter.getInstance(document, out);
        document.addTitle("BiletFlow ticket");
        document.open();
        for (Ticket ticket : tickets) {
            document.newPage();
            drawTicket(writer.getDirectContent(), ticket);
        }
        document.close();
        return out.toByteArray();
    }

    private void drawTicket(PdfContentByte canvas, Ticket ticket) {
        Event event = ticket.getEvent();

        ColumnText text = new ColumnText(canvas);
        // Text that doesn't fit is cut off here rather than printed over the QR code.
        text.setSimpleColumn(MARGIN, QR_BOTTOM + QR_SIZE + 16, PAGE_WIDTH - MARGIN, PageSize.A4.getHeight() - MARGIN);
        text.addElement(new Paragraph("BiletFlow", new Font(BOLD, 12)));
        Paragraph title = new Paragraph(event.getTitle(), new Font(BOLD, 24));
        title.setLeading(29);
        title.setSpacingAfter(16);
        text.addElement(title);

        PdfPTable details = new PdfPTable(new float[]{1, 3});
        details.setWidthPercentage(100);
        addRow(details, "Date and time", dateTime(event));
        addRow(details, "Venue", event.getVenue().getName() + "\n" + event.getVenue().getAddress());
        addRow(details, "Ticket type", ticket.getTicketType().getName());
        addRow(details, "Attendee", ticket.getAttendeeName() != null
                ? ticket.getAttendeeName() : ticket.getOwner().getEmail());
        addRow(details, "Ticket ID", ticket.getCode());
        addRow(details, "Status", ticket.getStatus() == TicketStatus.USED ? "Checked in" : "Valid");
        text.addElement(details);
        try {
            text.go();
        } catch (DocumentException e) {
            throw new IllegalStateException("Could not lay out ticket", e);
        }

        drawQr(canvas, qrCodeGenerator.toModules(ticket.getCode()));
        ColumnText.showTextAligned(canvas, Element.ALIGN_CENTER,
                new Phrase("Show this code at the entrance. Printed and digital copies are one and the same ticket.",
                        new Font(REGULAR, 10)),
                PAGE_WIDTH / 2, QR_BOTTOM - 14, 0);
    }

    // Vector rectangles instead of an image, so the code stays sharp at any printer resolution.
    private void drawQr(PdfContentByte canvas, BitMatrix modules) {
        float cell = QR_SIZE / modules.getWidth();
        float left = (PAGE_WIDTH - QR_SIZE) / 2;
        canvas.setGrayFill(0);
        for (int y = 0; y < modules.getHeight(); y++) {
            int x = 0;
            while (x < modules.getWidth()) {
                if (!modules.get(x, y)) {
                    x++;
                    continue;
                }
                int runStart = x;
                while (x < modules.getWidth() && modules.get(x, y)) {
                    x++;
                }
                canvas.rectangle(left + runStart * cell, QR_BOTTOM + QR_SIZE - (y + 1) * cell, (x - runStart) * cell, cell);
            }
        }
        canvas.fill();
    }

    private void addRow(PdfPTable table, String label, String value) {
        table.addCell(cell(label, new Font(BOLD, 11)));
        table.addCell(cell(value, new Font(REGULAR, 12)));
    }

    private PdfPCell cell(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPaddingBottom(7);
        return cell;
    }

    private String dateTime(Event event) {
        String start = event.getStartTime().format(DATE_TIME);
        return (event.getEndTime() != null ? start + " to " + event.getEndTime().format(DATE_TIME) : start)
                + " (Almaty time)";
    }

    private static BaseFont embeddedFont(String file) {
        try (InputStream in = TicketPdfGenerator.class.getResourceAsStream("/liberation/" + file)) {
            if (in == null) {
                throw new IllegalStateException("Font not found on the classpath: " + file);
            }
            return BaseFont.createFont(file, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, in.readAllBytes(), null);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (DocumentException e) {
            throw new IllegalStateException("Could not load font " + file, e);
        }
    }
}
