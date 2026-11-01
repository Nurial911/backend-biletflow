package kz.edu.biletflow.backend.dtos;

// Poster bytes plus what the controller needs to serve them.
public record PosterImage(byte[] bytes, String contentType, boolean isPublic) {
}
