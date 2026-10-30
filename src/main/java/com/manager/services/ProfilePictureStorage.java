package com.manager.services;

import com.azure.core.util.BinaryData;
import com.azure.core.util.Context;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.azure.storage.blob.models.BlobErrorCode;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.models.BlobDownloadContentResponse;
import com.azure.storage.blob.options.BlobParallelUploadOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

/**
 * Acesso ao container do Azure Blob com as fotos de perfil.
 * O container é privado e criado no primeiro uso; as fotos só chegam ao front pela API.
 */
@Component
public class ProfilePictureStorage {

    public record StoredPicture(byte[] content, String contentType) {}

    private final String connectionString;
    private final String containerName;
    private volatile BlobContainerClient container;

    public ProfilePictureStorage(@Value("${azure.storage.connection-string:}") String connectionString,
                                 @Value("${azure.storage.profile-pictures-container}") String containerName) {
        this.connectionString = connectionString;
        this.containerName = containerName;
    }

    private BlobContainerClient container() {
        if (container == null) {
            synchronized (this) {
                if (container == null) {
                    if (connectionString == null || connectionString.isBlank()) {
                        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                                "Armazenamento de imagens não configurado");
                    }
                    BlobContainerClient client = new BlobContainerClientBuilder()
                            .connectionString(connectionString)
                            .containerName(containerName)
                            .buildClient();
                    client.createIfNotExists();
                    container = client;
                }
            }
        }
        return container;
    }

    public void upload(String blobName, byte[] content, String contentType) {
        BlobClient blob = container().getBlobClient(blobName);
        BlobParallelUploadOptions options = new BlobParallelUploadOptions(BinaryData.fromBytes(content))
                .setHeaders(new BlobHttpHeaders().setContentType(contentType));
        blob.uploadWithResponse(options, null, Context.NONE);
    }

    public Optional<StoredPicture> download(String blobName) {
        try {
            BlobDownloadContentResponse response = container().getBlobClient(blobName)
                    .downloadContentWithResponse(null, null, null, Context.NONE);
            return Optional.of(new StoredPicture(
                    response.getValue().toBytes(),
                    response.getDeserializedHeaders().getContentType()));
        } catch (BlobStorageException e) {
            if (BlobErrorCode.BLOB_NOT_FOUND.equals(e.getErrorCode())) {
                return Optional.empty();
            }
            throw e;
        }
    }

    public void delete(String blobName) {
        container().getBlobClient(blobName).deleteIfExists();
    }
}
