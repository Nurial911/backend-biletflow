package kz.edu.biletflow.backend.services.impl;

import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

// Allowed poster image types. Detected from the file's first bytes, never from the
// client's Content-Type or file name, which are trivial to fake.
public enum PosterFormat {
    JPEG("jpg", MediaType.IMAGE_JPEG_VALUE),
    PNG("png", MediaType.IMAGE_PNG_VALUE),
    WEBP("webp", "image/webp");

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private final String extension;
    private final String contentType;

    PosterFormat(String extension, String contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    public String getExtension() {
        return extension;
    }

    public String getContentType() {
        return contentType;
    }

    public static Optional<PosterFormat> detect(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return Optional.of(JPEG);
        }
        if (bytes.length >= 8 && Arrays.equals(bytes, 0, 8, PNG_SIGNATURE, 0, 8)) {
            return Optional.of(PNG);
        }
        // RIFF <4-byte size> WEBP
        if (bytes.length >= 12 && ascii(bytes, 0, 4).equals("RIFF") && ascii(bytes, 8, 4).equals("WEBP")) {
            return Optional.of(WEBP);
        }
        return Optional.empty();
    }

    // Stored files are named <uuid>.<extension>, so the type is recovered from the key.
    public static PosterFormat fromKey(String key) {
        String extension = key.substring(key.lastIndexOf('.') + 1);
        for (PosterFormat format : values()) {
            if (format.extension.equals(extension)) {
                return format;
            }
        }
        throw new IllegalStateException("Unknown poster file type: " + key);
    }

    private static String ascii(byte[] bytes, int offset, int length) {
        return new String(bytes, offset, length, StandardCharsets.US_ASCII);
    }
}
