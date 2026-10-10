package de.ostms.lc.lc.api;
import jakarta.validation.constraints.*;import java.time.LocalDate;import java.util.UUID;
public record LcTaskRequest(@NotBlank @Size(max=500) String title,@Size(max=100) String assignedTo,LocalDate dueDate,UUID teamId){
 public LcTaskRequest(String title,String assignedTo,LocalDate dueDate){this(title,assignedTo,dueDate,null);}
}
