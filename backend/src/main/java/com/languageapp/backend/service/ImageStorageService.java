package com.languageapp.backend.service;

import java.io.IOException;

public interface ImageStorageService {
    /** Accepts only the PNG pixels produced by ProfileImageSanitizer. */
    String uploadProfileImage(byte[] png) throws IOException;
}
