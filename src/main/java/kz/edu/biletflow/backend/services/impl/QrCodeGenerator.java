package kz.edu.biletflow.backend.services.impl;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageConfig;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Map;

@Component
public class QrCodeGenerator {
    private static final int SIZE_PX = 400;
    private static final int CAMPAIGN_COLOR = 0xFF1E3A8A;
    public static final int CAMPAIGN_FRAME_PX = 28;

    // Black-on-white PNG; error correction M + a quiet zone keeps it scannable from paper and grayscale prints.
    public byte[] toPng(String content) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(encode(content, SIZE_PX, 2), "PNG", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // One cell per module, with the full 4-module quiet zone, for drawing the code as vector shapes in a PDF.
    public BitMatrix toModules(String content) {
        return encode(content, 0, 4);
    }

    // Campaign codes are indigo inside a thick frame, so staff can't confuse one with a black admission QR.
    public byte[] toCampaignPng(String content) {
        BufferedImage code = MatrixToImageWriter.toBufferedImage(encode(content, SIZE_PX, 2),
                new MatrixToImageConfig(CAMPAIGN_COLOR, MatrixToImageConfig.WHITE));
        int width = code.getWidth();
        int size = width + 2 * CAMPAIGN_FRAME_PX;
        // Plain pixel copies: the slim runtime image has no system fonts for AWT's drawing pipeline to load.
        int[] background = new int[size * size];
        Arrays.fill(background, CAMPAIGN_COLOR);
        BufferedImage framed = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        framed.setRGB(0, 0, size, size, background, 0, size);
        framed.setRGB(CAMPAIGN_FRAME_PX, CAMPAIGN_FRAME_PX, width, width, code.getRGB(0, 0, width, width, null, 0, width), 0, width);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(framed, "PNG", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private BitMatrix encode(String content, int size, int margin) {
        Map<EncodeHintType, Object> hints = Map.of(
                EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN, margin);
        try {
            return new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints);
        } catch (WriterException e) {
            throw new IllegalStateException("Could not generate QR code", e);
        }
    }
}
