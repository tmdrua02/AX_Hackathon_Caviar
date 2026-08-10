package com.haneul.medassist.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.DigestInputStream;
import java.util.HexFormat;
import java.util.UUID;

public interface ObjectStorage {
    StoredObject put(String ownerScope, String mediaType, InputStream input) throws IOException;
    byte[] read(String ownerScope, String objectKey) throws IOException;
    void delete(String ownerScope, String objectKey) throws IOException;
    record StoredObject(String objectKey, String checksum, long size) {}

    class StorageException extends IOException {
        private final String code;
        public StorageException(String code, Throwable cause) {
            super(code, cause);
            this.code = code;
        }
        public String code() { return code; }
    }
}

@Component
@Profile({"mock", "local", "prod"})
class LocalObjectStorage implements ObjectStorage {
    private final Path root;
    LocalObjectStorage(@Value("${app.storage-root:./server/uploads}") String root) throws IOException {
        this.root = Path.of(root).toAbsolutePath().normalize();
        Files.createDirectories(this.root);
    }

    @Override
    public StoredObject put(String ownerScope, String mediaType, InputStream input) throws IOException {
        Path owner = safeOwner(ownerScope);
        Path temporary = null;
        try {
            Files.createDirectories(owner);
            if (Files.getFileStore(root).getUsableSpace() < 10L * 1024 * 1024) {
                throw new StorageException("STORAGE_FULL", null);
            }
            String extension = mediaType.contains("audio") ? ".m4a" : ".bin";
            String key = UUID.randomUUID() + extension;
            Path target = owner.resolve(key);
            temporary = Files.createTempFile(owner, ".upload-", ".part");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (DigestInputStream digestInput = new DigestInputStream(input, digest)) {
                Files.copy(digestInput, temporary, StandardCopyOption.REPLACE_EXISTING);
            }
            long size = Files.size(temporary);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target);
            }
            temporary = null;
            return new StoredObject(key, HexFormat.of().formatHex(digest.digest()), size);
        } catch (StorageException error) {
            throw error;
        } catch (Exception error) {
            String code = isDiskFull(error) ? "STORAGE_FULL" : "STORAGE_WRITE_FAILED";
            throw new StorageException(code, error);
        } finally {
            if (temporary != null) Files.deleteIfExists(temporary);
        }
    }

    @Override public byte[] read(String ownerScope, String objectKey) throws IOException { return Files.readAllBytes(resolve(ownerScope, objectKey)); }
    @Override public void delete(String ownerScope, String objectKey) throws IOException { Files.deleteIfExists(resolve(ownerScope, objectKey)); }

    private Path safeOwner(String ownerScope) {
        if (!ownerScope.matches("[a-zA-Z0-9-]{1,80}")) throw new IllegalArgumentException("invalid owner scope");
        return root.resolve(ownerScope).normalize();
    }
    private Path resolve(String ownerScope, String key) {
        if (!key.matches("[a-zA-Z0-9._-]{1,120}")) throw new IllegalArgumentException("invalid object key");
        Path resolved = safeOwner(ownerScope).resolve(key).normalize();
        if (!resolved.startsWith(root)) throw new IllegalArgumentException("invalid object path");
        return resolved;
    }
    private boolean isDiskFull(Exception error) {
        if (error instanceof FileSystemException fileError) {
            String reason = fileError.getReason();
            return reason != null && (reason.contains("No space") || reason.contains("공간"));
        }
        return false;
    }
}
