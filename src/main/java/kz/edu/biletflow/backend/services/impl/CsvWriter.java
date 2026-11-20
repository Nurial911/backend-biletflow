package kz.edu.biletflow.backend.services.impl;

import jakarta.annotation.Nullable;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

// RFC 4180 CSV for the admin reports, built to open correctly in Excel:
// UTF-8 with a BOM (so Kazakh and Russian text isn't garbled), CRLF line ends, quotes where needed.
final class CsvWriter {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // Byte order mark: tells Excel the file is UTF-8.
    private static final char BOM = (char) 0xFEFF;

    private final StringBuilder out = new StringBuilder().append(BOM);
    private boolean lineStarted = false;

    CsvWriter row(Object... cells) {
        for (Object cell : cells) {
            cell(cell);
        }
        out.append("\r\n");
        lineStarted = false;
        return this;
    }

    byte[] toBytes() {
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void cell(@Nullable Object value) {
        if (lineStarted) {
            out.append(',');
        }
        lineStarted = true;
        if (value == null) {
            return;
        }
        String text = switch (value) {
            case BigDecimal amount -> amount.toPlainString();
            case LocalDateTime time -> DATE_TIME.format(time);
            case Number number -> number.toString();
            case Boolean bool -> bool.toString();
            default -> text(value.toString());
        };
        if (text.indexOf(',') >= 0 || text.indexOf('"') >= 0 || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) {
            text = '"' + text.replace("\"", "\"\"") + '"';
        }
        out.append(text);
    }

    // Free text comes from users (titles, emails, reasons). A leading = + - @ would make a spreadsheet
    // run it as a formula, so it is prefixed with an apostrophe (OWASP "CSV injection").
    private static String text(String value) {
        if (!value.isEmpty() && "=+-@\t\r".indexOf(value.charAt(0)) >= 0) {
            return "'" + value;
        }
        return value;
    }
}
