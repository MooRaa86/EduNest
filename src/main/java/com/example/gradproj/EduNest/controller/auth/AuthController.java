package com.example.gradproj.EduNest.controller.auth;

import com.example.gradproj.EduNest.dto.SimpleResponse;
import com.example.gradproj.EduNest.dto.auth.LoginRequestDto;
import com.example.gradproj.EduNest.dto.auth.RefreshTokenRequestDto;
import com.example.gradproj.EduNest.dto.auth.TokenPairResponseDto;
import com.example.gradproj.EduNest.dto.auth.forgetPassword.ForgetPasswordRequestDto;
import com.example.gradproj.EduNest.dto.auth.forgetPassword.ResetPasswordRequestDto;
import com.example.gradproj.EduNest.dto.auth.forgetPassword.VerifyForgetPasswordOtpDto;
import com.example.gradproj.EduNest.exception.jwt.InvalidRefreshTokenException;
import com.example.gradproj.EduNest.service.auth.LoginService;
import com.example.gradproj.EduNest.service.auth.RefreshTokenService;
import com.example.gradproj.EduNest.service.register.RegistrationService;
import com.example.gradproj.EduNest.utils.Constants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(
        name = "Authentication",
        description = "Authentication (login, refresh token, session revocation, forget password)"
)
public class AuthController {
    private final LoginService loginService;
    private final RefreshTokenService refreshTokenService;
    private final RegistrationService registerService;

    @Value("${app.auth.return-token-in-body:true}")
    private boolean returnTokenInBody;

    @PostMapping("/login-api")
    @Operation(summary = "login with email and password")
    public ResponseEntity<SimpleResponse> login(
            @Valid @RequestBody LoginRequestDto DTO
    ) {
        TokenPairResponseDto tokenPair = loginService.loginWithRefreshToken(DTO);
        String jwt = tokenPair.getAccessToken();
        String role = tokenPair.getRole();
        String refreshToken = tokenPair.getRefreshToken();

        SimpleResponse resp = new SimpleResponse();
        resp.addMessage("status", "Login successful");
        if (returnTokenInBody) {
            resp.addMessage("jwt", jwt);
        }
        if (refreshToken != null) {
            resp.addMessage("refreshToken", refreshToken);
        }
        resp.addMessage("Role", role);

        return ResponseEntity
                .status(HttpStatus.OK)
                .header(Constants.JWT_HEADER, jwt)
                .body(resp);
    }

    @PostMapping("/login-api/refresh")
    @Operation(summary = "rotate refresh token and issue new token pair")
    public ResponseEntity<SimpleResponse> refresh(
            @Valid @RequestBody RefreshTokenRequestDto dto
    ) {
        TokenPairResponseDto tokenPair = refreshTokenService.rotateRefreshToken(dto.getRefreshToken());
        SimpleResponse resp = new SimpleResponse();
        resp.addMessage("status", "Token rotated successfully");
        resp.addMessage("jwt", tokenPair.getAccessToken());
        resp.addMessage("refreshToken", tokenPair.getRefreshToken());
        resp.addMessage("Role", tokenPair.getRole());

        return ResponseEntity
                .status(HttpStatus.OK)
                .header(Constants.JWT_HEADER, tokenPair.getAccessToken())
                .body(resp);
    }

    @PostMapping("/login-api/logout")
    @Operation(summary = "logout single session by revoking refresh token")
    public ResponseEntity<SimpleResponse> logout(
            @Valid @RequestBody RefreshTokenRequestDto dto
    ) {
        refreshTokenService.logoutSingleSession(dto.getRefreshToken());
        SimpleResponse resp = new SimpleResponse();
        resp.addMessage("status", "Logout successful");
        return ResponseEntity.status(HttpStatus.OK).body(resp);
    }

    @PostMapping("/login-api/logout-all")
    @Operation(summary = "logout all sessions for current user")
    public ResponseEntity<SimpleResponse> logoutAll(
            HttpServletRequest request
    ) {
        String token = request.getHeader(Constants.JWT_HEADER);
        if (token == null || token.isBlank()) {
            throw new InvalidRefreshTokenException("Authorization token is required for logout-all");
        }
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        refreshTokenService.logoutAll(token);
        SimpleResponse resp = new SimpleResponse();
        resp.addMessage("status", "All sessions logged out successfully");
        return ResponseEntity.status(HttpStatus.OK).body(resp);
    }

    @PostMapping("/forget-password")
    @Operation(summary = "send otp via email for password reset")
    public ResponseEntity<SimpleResponse> forgetPassword(
            @Valid @RequestBody ForgetPasswordRequestDto dto) {

        registerService.forgetPassword(dto.getEmail());
        SimpleResponse resp = new SimpleResponse();
        resp.addMessage("message", "OTP has sent to your email");
        return ResponseEntity.status(HttpStatus.OK).body(resp);
    }

    @PostMapping("/forget-password/verify-otp")
    @Operation(summary = "verify the otp")
    public ResponseEntity<SimpleResponse> verifyForgetPasswordOtp(
            @Valid @RequestBody VerifyForgetPasswordOtpDto dto) {

        String resetToken = registerService.verifyForgetPasswordOtp(
                dto.getEmail(),
                dto.getOtp()
        );
        SimpleResponse resp = new SimpleResponse();
        resp.addMessage("message", "OTP verified successfully.");
        resp.addMessage("resetToken", resetToken);
        return ResponseEntity.status(HttpStatus.OK).cacheControl(CacheControl.noStore()).body(resp);
    }

    @PostMapping("/forget-password/reset")
    @Operation(summary = "reset password after verification")
    public ResponseEntity<SimpleResponse> resetPassword(
            @Valid @RequestBody ResetPasswordRequestDto dto) {

        registerService.resetPassword(
                dto.getEmail(),
                dto.getResetToken(),
                dto.getNewPassword()
        );
        SimpleResponse resp = new SimpleResponse();
        resp.addMessage("message", "password has been reset successfully");
        return ResponseEntity.status(HttpStatus.OK).body(resp);
    }
}
