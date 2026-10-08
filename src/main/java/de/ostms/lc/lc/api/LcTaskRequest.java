package de.ostms.lc.lc.api;
import jakarta.validation.constraints.*;import java.time.LocalDate;
public record LcTaskRequest(@NotBlank @Size(max=500) String title,@Size(max=100) String assignedTo,LocalDate dueDate){}
