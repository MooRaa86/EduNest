package com.example.gradproj.EduNest.service.admin;

import com.example.gradproj.EduNest.dto.auth.AdminAccResponse;
import com.example.gradproj.EduNest.dto.auth.RegisterAdminRequest;
import com.example.gradproj.EduNest.entity.Roles;
import com.example.gradproj.EduNest.entity.users.Admin;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.exception.registerExceptions.RoleNotFoundException;
import com.example.gradproj.EduNest.repository.RoleRepository;
import com.example.gradproj.EduNest.repository.users.AdminRepository;
import com.example.gradproj.EduNest.repository.users.UserRepository;
import com.example.gradproj.EduNest.utils.SystemUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminService {
    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final RoleRepository rolesRepository;
    private final UserRepository userRepository;

    public synchronized AdminAccResponse registerAdmin(RegisterAdminRequest request) {
        // Bootstrap is one-shot: guarded by the count check INSIDE a synchronized block
        // so two concurrent cold-start requests cannot both pass the check (P1-09).
        if(adminRepository.count() > 0){
            throw new globalLogicEx("Admin bootstrap is no longer available.");
        }

        if(userRepository.existsByEmail(request.getEmail())){
            throw new globalLogicEx("Email is already in use.");
        }

        Roles role = rolesRepository.findByName(SystemUtils.ADMIN)
                .orElseThrow(() -> new RoleNotFoundException("Error: Role ADMIN not found."));

        Admin admin = Admin.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(role)
                .build();

        adminRepository.save(admin);
        // Credentials never cross the API boundary (P1-09): the request plaintext is
        // encoded and persisted only; the response carries identity fields exclusively.
        return AdminAccResponse.builder()
                .id(admin.getPublicId())
                .email(admin.getEmail())
                .firstName(admin.getFirstName())
                .lastName(admin.getLastName())
                .build();
    }


    public List<AdminAccResponse> getAllAdmins() {
        return adminRepository.findAll().stream().map(
                a -> AdminAccResponse.builder()
                        .id(a.getPublicId())
                        .email(a.getEmail())
                        .firstName(a.getFirstName())
                        .lastName(a.getLastName())
                        .build()
        ).toList();
    }

    @org.springframework.transaction.annotation.Transactional
    public void deleteAdmin(String identifier) {
        if (adminRepository.count() <= 1) {
            throw new globalLogicEx("Cannot delete the last remaining admin.");
        }
        try {
            UUID publicId = UUID.fromString(identifier);
            if (adminRepository.existsByPublicId(publicId)) {
                adminRepository.deleteByPublicId(publicId);
                return;
            }
        } catch (IllegalArgumentException ignored) {
        }
        adminRepository.deleteByEmail(identifier);
    }

}
