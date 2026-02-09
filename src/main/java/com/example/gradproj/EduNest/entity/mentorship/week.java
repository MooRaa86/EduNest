package com.example.gradproj.EduNest.entity.mentorship;

import com.example.gradproj.EduNest.entity.BaseEntity;
import jakarta.persistence.Entity;
import lombok.*;
import lombok.experimental.SuperBuilder;


@SuperBuilder
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class week extends BaseEntity {
    private String title;

}
