package com.manager.services;

import com.manager.models.User;
import com.manager.repositories.UserRepository;
import com.manager.services.ProfilePictureStorage.StoredPicture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fotos de perfil: valida a imagem, guarda no Azure Blob e mantém em User.picture o nome
 * do arquivo. A imagem só sai pela API (GET /user/{id}/picture), para o próprio usuário.
 */
@Service
public class ProfilePictureService {

    private static final Logger log = LoggerFactory.getLogger(ProfilePictureService.class);

    public static final long MAX_SIZE_BYTES = 5 * 1024 * 1024;
    private static final String BLOB_PREFIX = "users/";

    // {userId}-{uuid}.{ext}: o uuid muda a cada envio e serve de ETag para o cache do front
    private static final Pattern FILE_NAME = Pattern.compile("^(\\d+)-[0-9a-f]{32}\\.(png|jpg|webp)$");

    enum ImageType {
        PNG("png", "image/png"),
        JPEG("jpg", "image/jpeg"),
        WEBP("webp", "image/webp");

        final String extension;
        final String contentType;

        ImageType(String extension, String contentType) {
            this.extension = extension;
            this.contentType = contentType;
        }
    }

    private final UserRepository userRepository;
    private final ProfilePictureStorage storage;

    public ProfilePictureService(UserRepository userRepository, ProfilePictureStorage storage) {
        this.userRepository = userRepository;
        this.storage = storage;
    }

    /** Envia ou troca a foto. Retorna vazio se o usuário não existir. */
    public Optional<User> upload(Integer userId, MultipartFile file) throws IOException {
        Optional<User> found = userRepository.findById(userId);
        if (found.isEmpty()) {
            return Optional.empty();
        }

        byte[] content = readImage(file);
        ImageType type = detectType(content);

        String fileName = userId + "-" + UUID.randomUUID().toString().replace("-", "") + "." + type.extension;
        storage.upload(BLOB_PREFIX + fileName, content, type.contentType);

        User user = found.get();
        String previousPicture = user.getPicture();
        user.setPicture(fileName);
        User saved = userRepository.save(user);

        // Só apaga a foto antiga depois que o banco já aponta para a nova
        deleteStoredPicture(userId, previousPicture);
        return Optional.of(saved);
    }

    /** Remove a foto; o usuário fica com picture = null e o front mostra a imagem padrão. */
    public Optional<User> delete(Integer userId) {
        Optional<User> found = userRepository.findById(userId);
        if (found.isEmpty()) {
            return Optional.empty();
        }

        User user = found.get();
        String previousPicture = user.getPicture();
        user.setPicture(null);
        User saved = userRepository.save(user);

        deleteStoredPicture(userId, previousPicture);
        return Optional.of(saved);
    }

    /**
     * Nome do arquivo da foto enviada pelo usuário, ou vazio se ele não tiver uma.
     * Valores antigos (URLs externas digitadas antes do upload existir) e nomes de arquivo
     * de outros usuários são ignorados.
     */
    public Optional<String> storedFileName(Integer userId, String picture) {
        if (picture == null) {
            return Optional.empty();
        }
        Matcher matcher = FILE_NAME.matcher(picture);
        if (!matcher.matches() || !matcher.group(1).equals(String.valueOf(userId))) {
            return Optional.empty();
        }
        return Optional.of(picture);
    }

    /** Conteúdo da foto do usuário; vazio se ele estiver sem foto (o controller usa a padrão). */
    public Optional<StoredPicture> download(User user) {
        return storedFileName(user.getId(), user.getPicture())
                .flatMap(fileName -> storage.download(BLOB_PREFIX + fileName));
    }

    /** Apaga do blob a foto do usuário. Falhas só são registradas: a foto já foi desvinculada dele. */
    public void deleteStoredPicture(Integer userId, String picture) {
        storedFileName(userId, picture).ifPresent(fileName -> {
            try {
                storage.delete(BLOB_PREFIX + fileName);
            } catch (RuntimeException e) {
                log.warn("Não foi possível apagar a foto antiga {}: {}", fileName, e.getMessage());
            }
        });
    }

    private byte[] readImage(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Envie a imagem no campo 'file'");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "A imagem deve ter no máximo 5 MB");
        }
        return file.getBytes();
    }

    /**
     * Identifica o tipo pelos primeiros bytes do arquivo, não pela extensão nem pelo
     * Content-Type enviados, que o cliente pode falsificar. SVG não é aceito porque pode conter script.
     */
    static ImageType detectType(byte[] content) {
        if (startsWith(content, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return ImageType.PNG;
        }
        if (startsWith(content, 0, 0xFF, 0xD8, 0xFF)) {
            return ImageType.JPEG;
        }
        if (startsWith(content, 0, 'R', 'I', 'F', 'F') && startsWith(content, 8, 'W', 'E', 'B', 'P')) {
            return ImageType.WEBP;
        }
        throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Tipo de arquivo não suportado. Envie uma imagem PNG, JPEG ou WEBP");
    }

    private static boolean startsWith(byte[] content, int offset, int... signature) {
        if (content.length < offset + signature.length) {
            return false;
        }
        int[] actual = new int[signature.length];
        for (int i = 0; i < signature.length; i++) {
            actual[i] = content[offset + i] & 0xFF;
        }
        return Arrays.equals(actual, signature);
    }
}
