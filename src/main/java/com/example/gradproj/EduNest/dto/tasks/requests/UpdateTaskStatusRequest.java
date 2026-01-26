package com.example.gradproj.EduNest.dto.tasks.requests;

import com.example.gradproj.EduNest.enums.tasks.TaskStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateTaskStatusRequest {
    @NotNull(message = "status is required")
    private TaskStatus status;
}
