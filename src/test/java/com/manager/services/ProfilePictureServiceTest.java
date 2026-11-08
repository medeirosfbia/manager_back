package com.manager.services;

import com.manager.models.User;
import com.manager.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ProfilePictureServiceTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0};
    private static final byte[] WEBP = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 0};
    private static final String OWN_PICTURE = "1-0123456789abcdef0123456789abcdef.png";

    @InjectMocks
    private ProfilePictureService service;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProfilePictureStorage storage;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1);
        user.setUsername("aluno");
    }

    private MockMultipartFile file(byte[] content, String name, String contentType) {
        return new MockMultipartFile("file", name, contentType, content);
    }

    private void userExists() {
        when(userRepository.findById(1)).thenReturn(Optional.of(user));
        lenient().when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void uploadStoresImageAndSavesFileName() throws Exception {
        userExists();

        User saved = service.upload(1, file(PNG, "foto.png", "image/png")).orElseThrow();

        ArgumentCaptor<String> blobName = ArgumentCaptor.forClass(String.class);
        verify(storage).upload(blobName.capture(), eq(PNG), eq("image/png"));
        assertTrue(blobName.getValue().matches("users/1-[0-9a-f]{32}\\.png"));
        assertEquals(blobName.getValue().substring("users/".length()), saved.getPicture());
    }

    @Test
    void uploadDetectsTypeByContentNotByName() throws Exception {
        userExists();

        service.upload(1, file(JPEG, "foto.png", "image/png"));
        verify(storage).upload(matches("users/1-[0-9a-f]{32}\\.jpg"), any(), eq("image/jpeg"));

        service.upload(1, file(WEBP, "foto", "application/octet-stream"));
        verify(storage).upload(matches("users/1-[0-9a-f]{32}\\.webp"), any(), eq("image/webp"));
    }

    @Test
    void uploadRejectsOtherFileTypes() {
        userExists();
        byte[] svg = "<svg onload=alert(1)>".getBytes();
        byte[] pdf = "%PDF-1.4".getBytes();
        byte[] exe = {'M', 'Z', 0, 0};

        for (byte[] content : new byte[][]{svg, pdf, exe}) {
            ResponseStatusException error = assertThrows(ResponseStatusException.class,
                    () -> service.upload(1, file(content, "foto.png", "image/png")));
            assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, error.getStatusCode());
        }
        verifyNoInteractions(storage);
    }

    @Test
    void uploadRejectsEmptyAndMissingFile() {
        userExists();

        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.upload(1, file(new byte[0], "foto.png", "image/png"))).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.upload(1, null)).getStatusCode());
        verifyNoInteractions(storage);
    }

    @Test
    void uploadRejectsLargeFile() {
        userExists();
        byte[] big = new byte[(int) ProfilePictureService.MAX_SIZE_BYTES + 1];
        System.arraycopy(PNG, 0, big, 0, PNG.length);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.upload(1, file(big, "foto.png", "image/png")));
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, error.getStatusCode());
        verifyNoInteractions(storage);
    }

    @Test
    void replacingPictureDeletesPreviousBlob() throws Exception {
        user.setPicture(OWN_PICTURE);
        userExists();

        service.upload(1, file(PNG, "nova.png", "image/png"));

        verify(storage).delete("users/1-0123456789abcdef0123456789abcdef.png");
    }

    @Test
    void neverDeletesExternalUrlsOrOtherUsersPictures() throws Exception {
        user.setPicture("https://example.com/foto.png");
        userExists();
        service.upload(1, file(PNG, "nova.png", "image/png"));

        service.deleteStoredPicture(1, "2-0123456789abcdef0123456789abcdef.png");
        service.deleteStoredPicture(1, "../1-0123456789abcdef0123456789abcdef.png");

        verify(storage, never()).delete(any());
    }

    @Test
    void uploadForMissingUserReturnsEmpty() throws Exception {
        when(userRepository.findById(1)).thenReturn(Optional.empty());

        assertTrue(service.upload(1, file(PNG, "foto.png", "image/png")).isEmpty());
        verifyNoInteractions(storage);
    }

    @Test
    void deleteClearsPictureAndRemovesBlob() {
        user.setPicture(OWN_PICTURE);
        userExists();

        User saved = service.delete(1).orElseThrow();

        assertNull(saved.getPicture());
        verify(storage).delete("users/1-0123456789abcdef0123456789abcdef.png");
    }

    @Test
    void deleteStillSucceedsWhenBlobRemovalFails() {
        user.setPicture(OWN_PICTURE);
        userExists();
        doThrow(new RuntimeException("falha de rede")).when(storage).delete(any());

        assertNull(service.delete(1).orElseThrow().getPicture());
    }

    @Test
    void downloadReturnsOwnPicture() {
        user.setPicture(OWN_PICTURE);

        service.download(user);

        verify(storage).download("users/" + OWN_PICTURE);
    }

    @Test
    void downloadIgnoresMissingExternalOrOtherUsersPictures() {
        for (String picture : new String[]{null, "", "https://example.com/foto.png",
                "2-0123456789abcdef0123456789abcdef.png", "../1-0123456789abcdef0123456789abcdef.png"}) {
            user.setPicture(picture);
            assertTrue(service.download(user).isEmpty());
        }
        verifyNoInteractions(storage);
    }
}
