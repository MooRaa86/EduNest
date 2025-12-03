package com.example.gradproj.EduNest.service;

import com.example.gradproj.EduNest.dto.RegisterRequestDto;
import com.example.gradproj.EduNest.entity.Roles;
import com.example.gradproj.EduNest.entity.UserEntity;
import com.example.gradproj.EduNest.repository.UserRepository;
import com.example.gradproj.EduNest.repository.roleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class registerServiceImpl implements RegisterationService{

    private final UserRepository userRepository;
    private final roleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public boolean registerUser(RegisterRequestDto registerRequestDto) {

        Roles role = roleRepository.findById(registerRequestDto.getRoleId())
                .orElseThrow(() -> new RuntimeException("Role not found"));

        String pass = passwordEncoder.encode(registerRequestDto.getPassword());

        UserEntity userEntity = UserEntity.builder()
                .fristName(registerRequestDto.getFirstName())
                .lastName(registerRequestDto.getLastName())
                .email(registerRequestDto.getEmail())
                .password(pass)
                .phoneNumber(registerRequestDto.getPhoneNumber())
                .roles(role)
                .build();

        UserEntity user = userRepository.save(userEntity);

        if(user != null){
            return true;
        }else{
            throw new RuntimeException("Registeration Failed!!");
        }
    }
}
