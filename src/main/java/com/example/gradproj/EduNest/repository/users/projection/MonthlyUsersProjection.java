package com.example.gradproj.EduNest.repository.users.projection;

public interface MonthlyUsersProjection {
    Integer getYear();
    Integer getMonth();
    Long getTotalUsers();
}
