package de.corporate.lc.document.api;

import de.corporate.lc.document.domain.DocumentType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record DocumentUpdateRequest(@NotNull DocumentType type,LocalDate documentDate,
                                    BigDecimal amount,@Size(max=3) String currency) {}
