package com.haneul.medassist.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

public interface ObjectStorage {
    StoredObject put(String ownerScope, String mediaType, InputStream input) throws IOException;
    byte[] read(String ownerScope, String objectKey) throws IOException;
    void delete(String ownerScope, String objectKey) throws IOException;
    record StoredObject(String objectKey, String checksum, long size) {}
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
        Files.createDirectories(owner);
        byte[] bytes = input.readAllBytes();
        String extension = mediaType.contains("audio") ? ".m4a" : ".bin";
        String key = UUID.randomUUID() + extension;
        Files.write(owner.resolve(key), bytes);
        return new StoredObject(key, sha256(bytes), bytes.length);
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
    private String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }
}
