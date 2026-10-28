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
    @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.NEVER)
    public String uploadProfileImage(byte[] png) throws IOException {
        Map<?, ?> uploadResult = cloudinary.uploader().upload(png, ObjectUtils.asMap(
                "folder", "languageapp/profiles",
                "resource_type", "image",
                "format", "png",
                "connect_timeout", 3000,
                "connection_request_timeout", 2000,
                "timeout", 10000
        ));

        Object value=uploadResult.get("secure_url");
        if(!(value instanceof String url))throw new IOException("Image provider returned no secure URL");
        try {
            var uri=java.net.URI.create(url);
            if(!"https".equals(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null)throw new IllegalArgumentException();
            return url;
        }catch(IllegalArgumentException invalid){throw new IOException("Image provider returned an invalid secure URL");}
    }
}
