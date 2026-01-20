package com.example.gradproj.EduNest.entity.quizEntity;

import com.example.gradproj.EduNest.entity.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Entity
@Table(name = "questions")
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Question extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_id", nullable = false)
    @NotNull(message = "Quiz must not be null")
    private Quiz quiz;


    @NotBlank(message = "Question text cannot be blank")
    @Size(max = 500, message = "Question text cannot exceed 500 characters")
    private String text;

    @Min(value = 0, message = "Points can't be negative")
    private int points;

    @Column(name = "question_order")
    @Min(value = 1, message = "Order must be at least 1")
    private int orderNumber;

    @NotBlank(message = "Empty Answer , Select an Option")
    private String correctAnswer;


    @NotBlank(message = "Option A cannot be blank")
    private String optionA;

    @NotBlank(message = "Option B cannot be blank")
    private String optionB;

    @NotBlank(message = "Option C cannot be blank")
    private String optionC;

    @NotBlank(message = "Option D cannot be blank")
    private String optionD;
}
