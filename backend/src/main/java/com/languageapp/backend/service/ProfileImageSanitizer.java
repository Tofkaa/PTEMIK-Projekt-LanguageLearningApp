package com.languageapp.backend.service;

import com.languageapp.backend.exception.BadRequestException;
import com.languageapp.backend.exception.UploadTooLargeException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;

/** Decode bounded raster input and upload only newly encoded pixels, never original metadata/payloads. */
@Component
public class ProfileImageSanitizer {
    public static final int MAX_BYTES = 5 * 1024 * 1024;
    public static final int MAX_EDGE = 4096;
    private static final long MAX_PIXELS = 16_000_000;
    private static final int AVATAR_EDGE = 250;
    private static final Set<String> FORMATS = Set.of("png", "jpeg", "jpg", "webp");

    public byte[] sanitize(MultipartFile file) {
        if (file == null || file.isEmpty()) throw invalid();
        if (file.getSize() > MAX_BYTES) throw new UploadTooLargeException();
        try (var stream = file.getInputStream()) {
            byte[] bytes = stream.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) throw new UploadTooLargeException();
            try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw invalid();
                ImageReader reader = readers.next();
                try {
                    if (!FORMATS.contains(reader.getFormatName().toLowerCase(Locale.ROOT))) throw invalid();
                    reader.setInput(input, true, true);
                    int width = reader.getWidth(0), height = reader.getHeight(0);
                    if (width <= 0 || height <= 0 || width > MAX_EDGE || height > MAX_EDGE
                            || (long) width * height > MAX_PIXELS) {
                        throw new BadRequestException("A kép legfeljebb 4096 × 4096 képpontos és 16 megapixeles lehet.");
                    }
                    boolean[] damaged = {false};
                    reader.addIIOReadWarningListener((source, warning) -> damaged[0] = true);
                    BufferedImage decoded = reader.read(0);
                    if (decoded == null || damaged[0]) throw invalid();
                    try {
                        return encodeAvatar(decoded);
                    } finally {
                        decoded.flush();
                    }
                } finally {
                    reader.dispose();
                }
            }
        } catch (IOException | IllegalArgumentException | IndexOutOfBoundsException ex) {
            throw invalid();
        }
    }

    private byte[] encodeAvatar(BufferedImage decoded) throws IOException {
        var avatar = new BufferedImage(AVATAR_EDGE, AVATAR_EDGE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = avatar.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            int edge = Math.min(decoded.getWidth(), decoded.getHeight());
            int x = (decoded.getWidth() - edge) / 2, y = (decoded.getHeight() - edge) / 2;
            graphics.drawImage(decoded, 0, 0, AVATAR_EDGE, AVATAR_EDGE, x, y, x + edge, y + edge, null);
        } finally {
            graphics.dispose();
        }
        try (var output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(avatar, "png", output)) throw new IOException("PNG encoder unavailable");
            return output.toByteArray();
        } finally {
            avatar.flush();
        }
    }

    private BadRequestException invalid() {
        return new BadRequestException("Érvénytelen vagy sérült kép. JPG, PNG vagy WEBP kép választható.");
    }
}
