package com.example.gradproj.EduNest.entity.mentorship;

import com.example.gradproj.EduNest.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "commission")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@SuperBuilder
public class Commission extends BaseEntity {

    @Column(nullable = false)
    private Double rate;
}
