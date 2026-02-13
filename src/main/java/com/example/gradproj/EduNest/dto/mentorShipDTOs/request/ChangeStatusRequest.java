package com.example.gradproj.EduNest.dto.mentorShipDTOs.request;

import com.example.gradproj.EduNest.enums.mentorShip.Status;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Setter @Getter @AllArgsConstructor @NoArgsConstructor
public class ChangeStatusRequest {
    @NotNull
    private Status status;

}
