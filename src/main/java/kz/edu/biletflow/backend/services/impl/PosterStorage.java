package kz.edu.biletflow.backend.services.impl;

import kz.edu.biletflow.backend.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

// Keeps poster images as files in app.uploads.dir (a Docker volume on the server).
// The DB stores only the key, i.e. the file name "<uuid>.<extension>".
@Slf4j
@Component
public class PosterStorage {
    private final Path root;

    public PosterStorage(@Value("${app.uploads.dir:./uploads}") String dir) {
        this.root = Path.of(dir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create uploads directory " + root, e);
        }
    }

    public String save(byte[] bytes, PosterFormat format) {
        String key = UUID.randomUUID() + "." + format.getExtension();
        try {
            // Write to a temp file first so a half-written poster is never served.
            Path temp = Files.createTempFile(root, "upload-", ".tmp");
            Files.write(temp, bytes);
            Files.move(temp, resolve(key), StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store poster", e);
        }
        return key;
    }

    public byte[] load(String key) {
        try {
            return Files.readAllBytes(resolve(key));
        } catch (NoSuchFileException e) {
            throw new ResourceNotFoundException("Poster file not found");
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read poster", e);
        }
    }

    public String copy(String key) {
        return save(load(key), PosterFormat.fromKey(key));
    }

    // Best effort: a leftover file only wastes disk space, so it must not fail the request.
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            log.warn("Could not delete poster file {}", key, e);
        }
    }

    private Path resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.getParent().equals(root)) {
            throw new IllegalArgumentException("Invalid poster key: " + key);
        }
        return path;
    }
}
