package com.example.gradproj.EduNest.repository.projects.projection;

import com.example.gradproj.EduNest.enums.project.ProjectStatus;

public interface ProjectAuthProjection {
    Long getId();
    String getMentorEmail();
    Long getMentorshipId();
    ProjectStatus getStatus();
    String getFilePath();
}
