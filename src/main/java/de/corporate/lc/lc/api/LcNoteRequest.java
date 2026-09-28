package de.corporate.lc.lc.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LcNoteRequest(@NotBlank @Size(max = 2000) String content) {}
