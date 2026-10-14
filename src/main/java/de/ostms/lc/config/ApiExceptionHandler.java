package de.ostms.lc.config;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import java.util.Map;
import java.util.NoSuchElementException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class, MaxUploadSizeExceededException.class})
    ResponseEntity<Map<String, String>> badRequest(Exception exception) {
        return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
    }
    /** The reason of an explicit status (e.g. 409 "Befund nicht mehr aktuell") reaches the screen instead of only the status text "Conflict". */
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    ResponseEntity<Map<String, String>> status(org.springframework.web.server.ResponseStatusException exception) {
        String reason = exception.getReason() == null || exception.getReason().isBlank() ? exception.getStatusCode().toString() : exception.getReason();
        return ResponseEntity.status(exception.getStatusCode()).body(Map.of("error", reason));
    }
    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<Map<String, String>> notFound(NoSuchElementException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> validation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Ungültige Eingabe");
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }
}
