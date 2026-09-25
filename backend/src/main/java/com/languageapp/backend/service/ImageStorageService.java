package com.languageapp.backend.service;

import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

public interface ImageStorageService {
    String uploadProfileImage(MultipartFile file) throws IOException;
}