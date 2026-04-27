package com.example.gradproj.EduNest.service.admin;

import com.example.gradproj.EduNest.dto.auth.AdminAccResponse;
import com.example.gradproj.EduNest.dto.auth.RegisterAdminRequest;
import com.example.gradproj.EduNest.entity.Roles;
import com.example.gradproj.EduNest.entity.users.Admin;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.exception.registerExceptions.RoleNotFoundException;
import com.example.gradproj.EduNest.repository.RoleRepository;
import com.example.gradproj.EduNest.repository.users.AdminRepository;
import com.example.gradproj.EduNest.utils.SystemUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminService {
    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final RoleRepository rolesRepository;

    public AdminAccResponse registerAdmin(RegisterAdminRequest request) {
        if(adminRepository.count() > 0){
            throw new globalLogicEx("Admin already exist");
        }

        Roles role = rolesRepository.findByName(SystemUtils.ADMIN)
                .orElseThrow(() -> new RoleNotFoundException("Error: Role ADMIN not found."));

        Admin admin = Admin.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .build();

        adminRepository.save(admin);
        return AdminAccResponse.builder()
                .email(admin.getEmail())
                .firstName(admin.getFirstName())
                .lastName(admin.getLastName())
                .password(request.getPassword())
                .build();
    }


    public List<Admin> getAllAdmins() {
        return adminRepository.findAll();
    }

    public void deleteAdmin(String email) {
        adminRepository.deleteByEmail(email);
    }

}
