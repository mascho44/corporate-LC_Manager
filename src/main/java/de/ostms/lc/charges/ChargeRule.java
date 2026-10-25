package de.ostms.lc.charges;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record ChargeRule(@NotNull ChargeType type,
 @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer=3,fraction=6) BigDecimal percent,
 @NotNull @DecimalMin("0") @Digits(integer=13,fraction=6) BigDecimal fixed,
 @NotNull @DecimalMin("0") @Digits(integer=13,fraction=6) BigDecimal minimum,
 @DecimalMin("0") @Digits(integer=13,fraction=6) BigDecimal maximum) {}
