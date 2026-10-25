package de.ostms.lc.config;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.NoSuchElementException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class, MaxUploadSizeExceededException.class})
    ResponseEntity<Map<String, String>> badRequest(Exception exception, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(Map.of("error", text(exception.getMessage(), request)));
    }
    /** The reason of an explicit status (e.g. 409 "Befund nicht mehr aktuell") reaches the screen instead of only the status text "Conflict". */
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    ResponseEntity<Map<String, String>> status(org.springframework.web.server.ResponseStatusException exception, HttpServletRequest request) {
        String reason = exception.getReason() == null || exception.getReason().isBlank() ? exception.getStatusCode().toString() : exception.getReason();
        return ResponseEntity.status(exception.getStatusCode()).body(Map.of("error", text(reason, request)));
    }
    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<Map<String, String>> notFound(NoSuchElementException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", text(exception.getMessage(), request)));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> validation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Ungültige Eingabe");
        return ResponseEntity.badRequest().body(Map.of("error", text(message, request)));
    }

    /** The browser sends the language of the screen in X-LC-Language; English messages come from the catalog of the server. */
    private static String text(String message, HttpServletRequest request) {
        return ServerMessages.translate(message == null ? "" : message, request == null ? null : request.getHeader("X-LC-Language"));
    }
}
