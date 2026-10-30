package com.manager.controllers;

import com.manager.models.User;
import com.manager.services.ProfilePictureService;
import com.manager.services.UserService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Optional;

@RestController
@RequestMapping("/user")
@CrossOrigin(origins = "*", allowedHeaders = "*", exposedHeaders = "ETag")
public class ProfilePictureController {

    private static final ClassPathResource DEFAULT_PICTURE = new ClassPathResource("avatar/default-avatar.png");

    private final ProfilePictureService profilePictureService;
    private final UserService userService;

    public ProfilePictureController(ProfilePictureService profilePictureService, UserService userService) {
        this.profilePictureService = profilePictureService;
        this.userService = userService;
    }

    /**
     * Foto do usuário, ou a imagem padrão se ele estiver sem foto.
     * Só o próprio usuário (ou um ADMIN) pode ver; o front busca com o header Authorization.
     */
    @GetMapping("/{id}/picture")
    public ResponseEntity<byte[]> picture(@PathVariable Integer id, WebRequest request) throws IOException {
        // Checado antes do 404 para não revelar quais ids existem
        if (!userService.canManage(id)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Optional<User> user = userService.getById(id);
        if (user.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        // O nome do arquivo muda a cada envio, então serve de ETag: o navegador revalida
        // e recebe 304 enquanto a foto não mudar.
        String etag = "\"" + profilePictureService.storedFileName(id, user.get().getPicture())
                .orElse("default") + "\"";
        if (request.checkNotModified(etag)) {
            return null;
        }

        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .eTag(etag)
                .cacheControl(CacheControl.noCache().cachePrivate())
                .header("X-Content-Type-Options", "nosniff");

        return profilePictureService.download(user.get())
                .map(picture -> response
                        .contentType(MediaType.parseMediaType(picture.contentType()))
                        .body(picture.content()))
                .orElseGet(() -> {
                    try {
                        return response.contentType(MediaType.IMAGE_PNG)
                                .body(DEFAULT_PICTURE.getContentAsByteArray());
                    } catch (IOException e) {
                        throw new IllegalStateException("Imagem padrão não encontrada", e);
                    }
                });
    }

    /** Envia ou troca a foto do usuário (multipart, campo "file"). */
    @PutMapping(value = "/{id}/picture", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<User> upload(@PathVariable Integer id,
                                       @RequestParam(value = "file", required = false) MultipartFile file)
            throws IOException {
        if (!userService.canManage(id)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return profilePictureService.upload(id, file)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    /** Remove a foto; a partir daí GET /user/{id}/picture devolve a imagem padrão. */
    @DeleteMapping("/{id}/picture")
    public ResponseEntity<User> delete(@PathVariable Integer id) {
        if (!userService.canManage(id)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return profilePictureService.delete(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }
}
