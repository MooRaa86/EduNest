package com.example.gradproj.EduNest.controller.admin;

import com.example.gradproj.EduNest.dto.SimpleResponse;
import com.example.gradproj.EduNest.dto.admin.AdminDashboardSummaryResponse;
import com.example.gradproj.EduNest.dto.admin.AdminMentorDetailResponse;
import com.example.gradproj.EduNest.dto.admin.AdminStudentDetailResponse;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.repository.users.projection.UserListProjection;
import com.example.gradproj.EduNest.service.admin.AdminUserDirectory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/users")
@Tag(name = "Admin User Directory", description = "APIs for Admin to manage and view user details and statistics")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserDirectoryController {

    private final AdminUserDirectory adminUserDirectory;

    @GetMapping("/{id}")
    @Operation(summary = "Get user details by ID", description = "Retrieve detailed information for a specific user (Mentor or Student) by their ID.")
    public ResponseEntity<SimpleResponse> getUserById(@PathVariable Long id) {
        SimpleResponse response = new SimpleResponse();
        Object userDetails = adminUserDirectory.getUserById(id);
        
        if (userDetails instanceof AdminMentorDetailResponse) {
            response.addMessage("mentorDetails", userDetails);
            response.addMessage("message", "Mentor details retrieved successfully");
        } else if (userDetails instanceof AdminStudentDetailResponse) {
            response.addMessage("studentDetails", userDetails);
            response.addMessage("message", "Student details retrieved successfully");
        } else {
            response.addMessage("message", "User details retrieved successfully");
            response.addMessage("userDetails", userDetails);
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/role/{roleName}")
    @Operation(summary = "Get users by role with pagination", description = "Retrieve a paginated list of users filtered by their role.")
    public ResponseEntity<SimpleResponse> getUsersByRole(
            @PathVariable String roleName,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        SimpleResponse response = new SimpleResponse();
        PageResponse<UserListProjection> usersPage = adminUserDirectory.getUsersByRole(roleName, page, size);
        response.addMessage("users", usersPage);
        response.addMessage("message", String.format("Paginated user list for role '%s' retrieved successfully", roleName));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/dashboard-summary")
    @Operation(summary = "Get admin dashboard summary", description = "Retrieve a combined summary of monthly user statistics and a paginated list of all users for the admin dashboard.")
    public ResponseEntity<SimpleResponse> getAdminDashboardSummary(
            @RequestParam(name = "months", required = false, defaultValue = "6") Integer months,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        SimpleResponse response = new SimpleResponse();
        AdminDashboardSummaryResponse summary = adminUserDirectory.getAdminDashboardSummary(months, page, size);
        response.addMessage("dashboardSummary", summary);
        response.addMessage("message", "Admin dashboard summary retrieved successfully");
        return ResponseEntity.ok(response);
    }
}
