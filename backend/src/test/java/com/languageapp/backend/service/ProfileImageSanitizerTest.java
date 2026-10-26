package com.languageapp.backend.service;

import com.languageapp.backend.exception.BadRequestException;
import com.languageapp.backend.exception.UploadTooLargeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.*;

class ProfileImageSanitizerTest {
    private final ProfileImageSanitizer sanitizer = new ProfileImageSanitizer();
    MockMultipartFile file(byte[] bytes) { return new MockMultipartFile("file", "spoofed.svg", "image/svg+xml", bytes); }
    byte[] raster(String format) throws Exception {
        var image = new BufferedImage(4, 2, BufferedImage.TYPE_INT_RGB);
        for (int x=0;x<4;x++) for (int y=0;y<2;y++) image.setRGB(x,y,0xff00ff00);
        var output = new ByteArrayOutputStream(); assertTrue(ImageIO.write(image,format,output)); return output.toByteArray();
    }
    @ParameterizedTest @ValueSource(strings={"png","jpeg"})
    void trustsDecodedPixelsRatherThanMimeOrFilenameAndOutputsOnlyPng(String format) throws Exception {
        byte[] output = sanitizer.sanitize(file(raster(format)));
        assertArrayEquals(new byte[]{(byte)137,80,78,71,13,10,26,10}, Arrays.copyOf(output,8));
        var avatar=ImageIO.read(new ByteArrayInputStream(output));
        assertEquals(250,avatar.getWidth());assertEquals(250,avatar.getHeight());
        var color=new java.awt.Color(avatar.getRGB(125,125),true);
        assertTrue(color.getGreen() >= 250 && color.getRed() < 5 && color.getBlue() < 5);
    }
    @Test void decodesWebpAndPreservesTheAdvertisedFormatSupport() throws Exception {
        byte[] webp=Base64.getDecoder().decode("UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEADsD+JaQAA3AAAAAA");
        var avatar=ImageIO.read(new ByteArrayInputStream(sanitizer.sanitize(file(webp))));
        assertEquals(250,avatar.getWidth());assertEquals(250,avatar.getHeight());
    }
    @ParameterizedTest @ValueSource(strings={"", "<svg xmlns='http://www.w3.org/2000/svg'><script>alert(1)</script></svg>","<html>not a raster</html>","GIF89a"})
    void rejectsEmptyScriptableAndTruncatedContentDespiteImageMime(String content) {
        assertThrows(BadRequestException.class,()->sanitizer.sanitize(file(content.getBytes(StandardCharsets.UTF_8))));
    }
    @Test void rejectsNonAllowlistedRasterEvenThoughItCanBeDecoded() throws Exception {
        assertThrows(BadRequestException.class,()->sanitizer.sanitize(file(raster("bmp"))));
    }
    @Test void stripsTrailingForeignPayloadFromAnOtherwiseValidImage() throws Exception {
        byte[] image=raster("png"), suffix="<script>foreign-payload</script>".getBytes(StandardCharsets.UTF_8);
        byte[] withSuffix=Arrays.copyOf(image,image.length+suffix.length);System.arraycopy(suffix,0,withSuffix,image.length,suffix.length);
        byte[] normalized=sanitizer.sanitize(file(withSuffix));
        assertFalse(new String(normalized,StandardCharsets.ISO_8859_1).contains("foreign-payload"));
        assertNotNull(ImageIO.read(new ByteArrayInputStream(normalized)));
    }
    @Test void rejectsHugeDeclaredDimensionsBeforePixelAllocation() throws Exception {
        byte[] png=raster("png");
        ByteBuffer.wrap(png).putInt(16,100_000).putInt(20,100_000);
        var crc=new CRC32();crc.update(png,12,17);ByteBuffer.wrap(png).putInt(29,(int)crc.getValue());
        var error=assertThrows(BadRequestException.class,()->sanitizer.sanitize(file(png)));
        assertTrue(error.getMessage().contains("4096"));
    }
    @Test void enforcesActualBytesEvenWhenMultipartSizeLies() {
        var large=new MockMultipartFile("file","avatar.png","image/png",new byte[ProfileImageSanitizer.MAX_BYTES+1]) {
            @Override public long getSize() { return 1; }
        };
        assertThrows(UploadTooLargeException.class,()->sanitizer.sanitize(large));
    }
    @Test void rejectsOversizedMultipartBeforeReadingItsStream() {
        var large=new MockMultipartFile("file","avatar.png","image/png",new byte[]{1}) {
            @Override public long getSize() { return ProfileImageSanitizer.MAX_BYTES+1; }
            @Override public java.io.InputStream getInputStream() { fail("Must reject before reading");return null; }
        };
        assertThrows(UploadTooLargeException.class,()->sanitizer.sanitize(large));
    }
    @Test void rejectsDamagedPixelDataAfterARecognizedHeader() throws Exception {
        byte[] png=raster("png");
        assertThrows(BadRequestException.class,()->sanitizer.sanitize(file(Arrays.copyOf(png,40))));
    }
}
