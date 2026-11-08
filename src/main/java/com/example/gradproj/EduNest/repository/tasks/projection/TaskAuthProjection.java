package com.example.gradproj.EduNest.repository.tasks.projection;

import com.example.gradproj.EduNest.enums.tasks.TaskStatus;

public interface TaskAuthProjection {
    Long getId();
    Long getWeekId();
    String getMentorEmail();
    Long getMentorshipId();
    TaskStatus getStatus();
    String getFilePath();
}
