package com.example.gradproj.EduNest.dto.lectures;

import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.validator.constraints.URL;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UpdeteLectureRequest {

    @Size(min = 3, max = 150, message = "title must be between 3 and 150 characters")
    private String title;

    @Size(max = 500, message = "lecture Url max length is 500")
    @URL(message = "lecture Url must be a valid URL")
    private String lectureUrl;
}
