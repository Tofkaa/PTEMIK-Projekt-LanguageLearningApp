package com.languageapp.backend.service;

import com.cloudinary.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CloudinaryTimeoutTest {
    @Test void uploadSetsConnectPoolAndReadBoundsAndRequiresHttps()throws Exception {
        var service=new CloudinaryStorageServiceImpl("cloudinary://test-key:test-secret@test-cloud");
        Cloudinary sdk=mock(Cloudinary.class);Uploader uploader=mock(Uploader.class);when(sdk.uploader()).thenReturn(uploader);
        ReflectionTestUtils.setField(service,"cloudinary",sdk);
        when(uploader.upload(any(byte[].class),anyMap())).thenReturn(Map.of("secure_url","https://images.example.test/profile.png"));
        assertEquals("https://images.example.test/profile.png",service.uploadProfileImage(new byte[]{1}));
        var options=ArgumentCaptor.forClass(Map.class);verify(uploader).upload(any(byte[].class),options.capture());
        assertEquals(3000,options.getValue().get("connect_timeout"));assertEquals(2000,options.getValue().get("connection_request_timeout"));assertEquals(10000,options.getValue().get("timeout"));
        when(uploader.upload(any(byte[].class),anyMap())).thenReturn(Map.of("secure_url","http://unsafe.example.test/photo"));
        assertThrows(java.io.IOException.class,()->service.uploadProfileImage(new byte[]{1}));
    }
}
