package de.corporate.lc.document.api;

import de.corporate.lc.document.domain.DocumentType;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record DocumentInboxAttachRequest(@NotNull UUID lcId, @NotNull DocumentType documentType, LocalDate documentDate) { }
