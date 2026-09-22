package de.corporate.lc.imports.api;
import java.math.BigDecimal; import java.time.LocalDate; import java.util.List;
public record SwiftImportPreview(String messageType,String reference,String applicant,String beneficiary,BigDecimal amount,String currency,LocalDate issueDate,LocalDate expiryDate,String expiryPlace,String amendmentNumber,List<String> requiredDocuments,boolean duplicate,boolean valid,List<String> errors,List<String> warnings,List<SwiftFieldView> rawFields) {}
