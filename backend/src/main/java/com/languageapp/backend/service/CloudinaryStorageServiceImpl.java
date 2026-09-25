package com.languageapp.backend.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Slf4j
@Service
public class CloudinaryStorageServiceImpl implements ImageStorageService {

    private final Cloudinary cloudinary;

    public CloudinaryStorageServiceImpl(@Value("${cloudinary.url}") String cloudinaryUrl) {
        this.cloudinary = new Cloudinary(cloudinaryUrl);
    }

    @Override
    public String uploadProfileImage(MultipartFile file) throws IOException {
        log.info("Képfeltöltés indítása a külső szolgáltató felé: {}", file.getOriginalFilename());

        Map uploadResult = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                "folder", "languageapp/profiles",
                "transformation", "c_fill,g_face,w_250,h_250" // Smart crop: Arc keresése és 250x250-es négyzetre vágás
        ));

        String secureUrl = uploadResult.get("secure_url").toString();
        log.info("Sikeres képfeltöltés. URL: {}", secureUrl);

        return secureUrl;
    }
}