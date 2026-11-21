package kz.edu.biletflow.backend.services.impl;

import kz.edu.biletflow.backend.i18n.Translator;
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
import java.util.List;
import java.util.Locale;

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

    private static final BaseFont REGULAR = embeddedFont("LiberationSans-Regular.ttf");
    private static final BaseFont BOLD = embeddedFont("LiberationSans-Bold.ttf");

    private final QrCodeGenerator qrCodeGenerator;
    private final Translator translator;

    // Labels in the given language; the event's and attendee's own texts stay as written.
    public byte[] generate(List<Ticket> tickets, Locale locale) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, MARGIN, MARGIN, MARGIN, MARGIN);
        PdfWriter writer = PdfWriter.getInstance(document, out);
        document.addTitle(translator.get(locale, "pdf.title"));
        document.open();
        for (Ticket ticket : tickets) {
            document.newPage();
            drawTicket(writer.getDirectContent(), ticket, locale);
        }
        document.close();
        return out.toByteArray();
    }

    private void drawTicket(PdfContentByte canvas, Ticket ticket, Locale locale) {
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
        addRow(details, label(locale, "pdf.dateTime"), dateTime(event, locale));
        addRow(details, label(locale, "pdf.venue"), event.getVenue().getName() + "\n" + event.getVenue().getAddress());
        addRow(details, label(locale, "pdf.ticketType"), ticket.getTicketType().getName());
        String place = translator.seatLabel(ticket.getSectionName(), ticket.getRowLabel(), ticket.getSeatNumber(), locale);
        if (place != null) {
            addRow(details, label(locale, ticket.getRowLabel() != null ? "pdf.seat" : "pdf.section"), place);
        }
        addRow(details, label(locale, "pdf.attendee"), ticket.getAttendeeName() != null
                ? ticket.getAttendeeName() : ticket.getOwner().getEmail());
        addRow(details, label(locale, "pdf.ticketId"), ticket.getCode());
        addRow(details, label(locale, "pdf.status"),
                label(locale, ticket.getStatus() == TicketStatus.USED ? "pdf.status.used" : "pdf.status.valid"));
        text.addElement(details);
        try {
            text.go();
        } catch (DocumentException e) {
            throw new IllegalStateException("Could not lay out ticket", e);
        }

        drawQr(canvas, qrCodeGenerator.toModules(ticket.getCode()));
        ColumnText.showTextAligned(canvas, Element.ALIGN_CENTER,
                new Phrase(label(locale, "pdf.footer"),
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

    private String dateTime(Event event, Locale locale) {
        return event.getEndTime() != null
                ? translator.get(locale, "pdf.whenRange", event.getStartTime(), event.getEndTime())
                : translator.get(locale, "pdf.when", event.getStartTime());
    }

    private String label(Locale locale, String key) {
        return translator.get(locale, key);
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
