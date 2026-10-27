package com.languageapp.backend.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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
    public String uploadProfileImage(byte[] png) throws IOException {
        Map<?, ?> uploadResult = cloudinary.uploader().upload(png, ObjectUtils.asMap(
                "folder", "languageapp/profiles",
                "resource_type", "image",
                "format", "png"
        ));

        String secureUrl = uploadResult.get("secure_url").toString();

        return secureUrl;
    }
}
