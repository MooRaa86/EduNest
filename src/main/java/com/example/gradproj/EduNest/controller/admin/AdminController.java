package com.example.gradproj.EduNest.controller.admin;

import com.example.gradproj.EduNest.dto.SimpleResponse;
import com.example.gradproj.EduNest.dto.auth.RegisterAdminRequest;
import com.example.gradproj.EduNest.service.admin.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin Gate", description = "APIs for managing Admin accounts (register, get all, delete)")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @PostMapping("/register")
    @Operation(summary = "Register first admin (setup)", description = "Register the first admin account. Can only be used when no admins exist.")
    public ResponseEntity<SimpleResponse> registerAdmin(@Valid @RequestBody RegisterAdminRequest request) {
        SimpleResponse response = new SimpleResponse();
        response.addMessage("admin", adminService.registerAdmin(request));
        response.addMessage("message", "Admin registered successfully");
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
//    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get all admins", description = "Retrieve list of all admin accounts (Admin only)")
    public ResponseEntity<SimpleResponse> getAllAdmins() {
        SimpleResponse response = new SimpleResponse();
        response.addMessage("message", "Admins retrieved successfully");
        response.addMessage("admins", adminService.getAllAdmins());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{email}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete admin by email", description = "Delete an admin account by email (Admin only)")
    public ResponseEntity<SimpleResponse> deleteAdmin(@PathVariable String email) {
        adminService.deleteAdmin(email);
        SimpleResponse response = new SimpleResponse();
        response.addMessage("message", "Admin deleted successfully");
        return ResponseEntity.ok(response);
    }
}
