package kz.edu.biletflow.backend.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

// Body of every error answer. `code` is the stable, machine-readable reason (ErrorCode);
// `message` is English text for people and may change.
@Getter
@Setter
@AllArgsConstructor
public class ErrorResponse {
    private int status;
    private ErrorCode code;
    private String message;
    private LocalDateTime timestamp;
}
