package com.manager.controllers;

import com.manager.models.User;
import com.manager.repositories.UserRepository;
import com.manager.security.JwtAuthFilter;
import com.manager.security.JwtService;
import com.manager.services.ProfilePictureService;
import com.manager.services.ProfilePictureStorage.StoredPicture;
import com.manager.services.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProfilePictureController.class)
@AutoConfigureMockMvc(addFilters = false)
public class ProfilePictureControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProfilePictureService profilePictureService;

    @MockBean
    private UserService userService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private JwtAuthFilter jwtAuthFilter;

    private static final String PICTURE = "1-0123456789abcdef0123456789abcdef.png";

    private User user;
    private MockMultipartFile file;

    @BeforeEach
    void setup() {
        user = new User();
        user.setId(1);
        user.setUsername("aluno");
        user.setPicture(PICTURE);

        file = new MockMultipartFile("file", "foto.png", "image/png", new byte[]{1, 2, 3});
        Mockito.when(userService.canManage(any())).thenReturn(true);
    }

    @Test
    void testUploadPicture() throws Exception {
        Mockito.when(profilePictureService.upload(eq(1), any())).thenReturn(Optional.of(user));

        mockMvc.perform(multipart("/user/1/picture").file(file).with(request -> {
                    request.setMethod("PUT");
                    return request;
                }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.picture").value(user.getPicture()));
    }

    @Test
    void testUploadForbiddenForOtherUser() throws Exception {
        Mockito.when(userService.canManage(1)).thenReturn(false);

        mockMvc.perform(multipart("/user/1/picture").file(file).with(request -> {
                    request.setMethod("PUT");
                    return request;
                }))
                .andExpect(status().isForbidden());

        Mockito.verify(profilePictureService, Mockito.never()).upload(any(), any());
    }

    @Test
    void testUploadUserNotFound() throws Exception {
        Mockito.when(profilePictureService.upload(eq(1), any())).thenReturn(Optional.empty());

        mockMvc.perform(multipart("/user/1/picture").file(file).with(request -> {
                    request.setMethod("PUT");
                    return request;
                }))
                .andExpect(status().isNotFound());
    }

    @Test
    void testDeletePicture() throws Exception {
        user.setPicture(null);
        Mockito.when(profilePictureService.delete(1)).thenReturn(Optional.of(user));

        mockMvc.perform(delete("/user/1/picture"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.picture").doesNotExist());
    }

    @Test
    void testDeleteForbiddenForOtherUser() throws Exception {
        Mockito.when(userService.canManage(1)).thenReturn(false);

        mockMvc.perform(delete("/user/1/picture"))
                .andExpect(status().isForbidden());

        Mockito.verify(profilePictureService, Mockito.never()).delete(any());
    }

    @Test
    void testGetOwnPicture() throws Exception {
        Mockito.when(userService.getById(1)).thenReturn(Optional.of(user));
        Mockito.when(profilePictureService.storedFileName(1, PICTURE)).thenReturn(Optional.of(PICTURE));
        Mockito.when(profilePictureService.download(user))
                .thenReturn(Optional.of(new StoredPicture(new byte[]{9, 8}, "image/png")));

        mockMvc.perform(get("/user/1/picture"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(new byte[]{9, 8}))
                .andExpect(header().string("ETag", "\"" + PICTURE + "\""))
                .andExpect(header().string("Cache-Control", "no-cache, private"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void testGetPictureNotModified() throws Exception {
        Mockito.when(userService.getById(1)).thenReturn(Optional.of(user));
        Mockito.when(profilePictureService.storedFileName(1, PICTURE)).thenReturn(Optional.of(PICTURE));

        mockMvc.perform(get("/user/1/picture").header("If-None-Match", "\"" + PICTURE + "\""))
                .andExpect(status().isNotModified());

        Mockito.verify(profilePictureService, Mockito.never()).download(any());
    }

    @Test
    void testGetPictureForbiddenForOtherUser() throws Exception {
        Mockito.when(userService.canManage(1)).thenReturn(false);

        mockMvc.perform(get("/user/1/picture"))
                .andExpect(status().isForbidden());

        Mockito.verify(userService, Mockito.never()).getById(any());
        Mockito.verify(profilePictureService, Mockito.never()).download(any());
    }

    @Test
    void testGetDefaultPictureWhenUserHasNone() throws Exception {
        user.setPicture(null);
        Mockito.when(userService.getById(1)).thenReturn(Optional.of(user));
        Mockito.when(profilePictureService.storedFileName(1, null)).thenReturn(Optional.empty());
        Mockito.when(profilePictureService.download(user)).thenReturn(Optional.empty());

        mockMvc.perform(get("/user/1/picture"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(header().string("ETag", "\"default\""));
    }

    @Test
    void testGetPictureUserNotFound() throws Exception {
        Mockito.when(userService.getById(1)).thenReturn(Optional.empty());

        mockMvc.perform(get("/user/1/picture"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testOldPublicRouteNoLongerExists() throws Exception {
        mockMvc.perform(get("/user/picture/" + PICTURE))
                .andExpect(status().isNotFound());
    }
}
