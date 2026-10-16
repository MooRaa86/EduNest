# EduNest Security Audit & Recommendations

An initial security scan of the **EduNest** project code has been performed. Several security vulnerabilities and issues have been identified that must be addressed before moving the project to production. 
The report is divided into several sections with proposed solutions for each issue, without modifying the current code directly as requested.

---

## 1. CORS Configuration (Cross-Origin Resource Sharing)
**Issue:**
In the `ProjectSecurityProdconfig.java` file, CORS is configured to allow any origin using `("*")` while `setAllowCredentials(true)` is also enabled. 
This configuration is considered a security risk and modern browsers block this combination, which may cause frontend requests to fail.

**Proposed Solution:**
- Specify the exact domains allowed to communicate with the backend instead of using `*`.
- Example: 
  ```java
  config.setAllowedOriginPatterns(Arrays.asList("https://your-frontend-domain.com"));
  ```

---

## 2. JWT Configuration (JSON Web Tokens)
**Issue 1: Default Secret Key**
In `JwtService.java`, there is a fallback to a `DEFAULT_SECRET`. If the Secret Key is not set in the environment variables in production, the system will use this default secret. If this secret is known, any attacker can forge tokens with Admin privileges (Token Forgery).

**Proposed Solution:**
- Remove the Default Secret completely, and let the application throw an exception if `JWT_SECRET_KEY` is not provided in the production environment.

**Issue 2: Token Expiration**
The current token validity period is 7 days `7 * (24 * (1000 * 60 * 60))`. This is an extremely long time for an Access Token. If the token is stolen, it remains valid for a whole week.

**Proposed Solution:**
- Reduce the Access Token duration to be short (e.g., 15 minutes to 1 hour).
- Implement a Refresh Token mechanism with a longer duration (e.g., 7 days) to renew the Access Token automatically.

---

## 3. File Upload Vulnerabilities
There is a lack of file type validation in some services, allowing malicious files (like `.exe` or `.html` scripts containing XSS) to be uploaded.

**Issue 1: `ImageStorageService.java` (in `saveCoverImage` method)**
This method only checks for the presence of a dot `.` in the filename. It does not verify if the uploaded file is actually an image (Missing MIME type check).

**Issue 2: `TaskFileStorageService.java`**
The method extracts the file extension and appends it to a UUID, but there is no validation to prevent dangerous or unexpected extensions from being uploaded.

**Proposed Solution:**
- Implement a white-list of allowed extensions and MIME Types (e.g., `.jpg, .png, .pdf, .docx`) and reject any other files.
- Use a library like Apache Tika to verify the actual file type (Magic Bytes) rather than relying solely on the extension in the filename.

---

## 4. Open Access Permissions (Access Control & Endpoints)
**Issue:**
In the production settings (`ProjectSecurityProdconfig.java`), general access (`permitAll`) is granted to file paths: `"/uploads/**"` and `"/files/**"`.
Based on `WebConfig.java`, the `uploads/` directory is entirely publicly accessible.
If these directories contain sensitive student files (like assignments, certificates, or ID cards), anyone can access them if they know the URL.

**Proposed Solution:**
- Do not serve sensitive files as static files.
- Create a dedicated controller for serving files (e.g., `FileController`) that first verifies the user's authorization (Token Validation & Authorization) before returning the resource.

---

## 5. Protection against Brute Force and DDoS Attacks
**Issue:**
There is no Rate Limiting on sensitive endpoints such as:
- `/login-api`
- `/api/v1/register/**`
- `/forget-password/**`

This allows attackers to try numerous passwords (Brute Force) or send excessive fake OTP requests, draining SMS/Email quotas.

**Proposed Solution:**
- Use a library like **Bucket4j** or rely on **Spring Boot Rate Limiter** to restrict the number of requests per IP within a specific timeframe.

---

## 6. CSRF Settings (Cross-Site Request Forgery)
**Issue:**
CSRF is disabled `csrf.disable()`. This setting is safe **only** if the application relies entirely on the Authorization Header (`Bearer Token`) and never uses Cookies (including JSESSIONID) for authentication.

**Proposed Solution:**
- Ensure you do not use Cookies at all for authentication processes. If you are storing the token in an `HttpOnly Cookie` on the frontend, you must re-enable CSRF protection.

---

### Summary of Required Fixes:
1. Correct CORS settings and specify specific domains for Production.
2. Shorten the JWT duration and remove the default Secret.
3. Add strict validation for uploaded file types (Extension & MIME Type Check).
4. Protect the `uploads/` directories from direct access if they contain private user files.
5. Add a Rate Limiter to login and registration pages.

---

## 7. Security Config Proposal

Here is a proposal for how the code should look in the **`ProjectSecurityProdconfig.java`** file to avoid the previous vulnerabilities (CORS, open paths, etc.):

```java
package com.example.gradproj.EduNest.config.security;

import com.example.gradproj.EduNest.filters.JwtTokenGeneratorFilter;
import com.example.gradproj.EduNest.filters.JwtTokenValidatorFilter;
// ... (Your imports) ...
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.Arrays;
import java.util.Collections;

@Profile("prod")
@Configuration
@EnableWebSecurity
public class ProjectSecurityProdconfig {

    // ... (Dependencies injection) ...

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .sessionManagement(smc -> smc.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .cors(corsConfig -> corsConfig.configurationSource(request -> {
                CorsConfiguration config = new CorsConfiguration();
                // 1. Specify the actual domain instead of *
                config.setAllowedOriginPatterns(Collections.singletonList("https://your-frontend-domain.com"));
                config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
                config.setAllowCredentials(true);
                config.setAllowedHeaders(Collections.singletonList("*"));
                config.setExposedHeaders(Collections.singletonList("Authorization"));
                config.setMaxAge(3600L);
                return config;
            }))
            .csrf(csrf -> csrf.disable()) // Assuming cookies are not used at all
            .addFilterAfter(jwtTokenGeneratorFilter, BasicAuthenticationFilter.class)
            .addFilterBefore(jwtTokenValidatorFilter, BasicAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                // 2. Secure uploads and files paths by requiring authentication
                .requestMatchers(
                        "/api/auth/**",
                        "/api/v1/register/**",
                        "/login-api",
                        "/forget-password/**"
                ).permitAll()
                // It is not recommended to open Swagger in Production, but if necessary:
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                // 3. Any other request must be Authenticated
                .anyRequest().authenticated()
            );

        // ... (Exception handling and disabling form login) ...
        return http.build();
    }
}
```

---

## 8. IDOR (Insecure Direct Object Reference) Vulnerabilities Check

During the initial scan of the Controllers and Services, the following was found:

### The Positive Aspect (Well Protected):
- Endpoints for submitting tasks (`TaskSubmissionController`), grading them, and the profile endpoints (`ProfileService` and `MentorViewStudentProfileController`) are excellently protected. The system relies on `SecurityContextHolder.getContext().getAuthentication().getName()` to identify the current user.
- Even when a Mentor queries data for a specific student `/{studentId}`, the `validateMentorHasAccessToStudent` function is called, which verifies against the database (`enrollmentRepository`) that this Mentor is indeed responsible for this student. **This is an excellent implementation of protection against IDOR.**

### The Negative Aspect (Clear IDOR Vulnerability):
- **In `FileController.java` (`/files` path)**:
  The `serveFile` function receives a file path and returns it as a Download. The problem is that **there is no authorization check** for the user requesting the file.
  Since the `/files/**` path is accessible to everyone (`permitAll`) in the old Security settings, this means that **anyone** can input a file name (as long as it is inside the `uploads` folder) and download it directly, even if it is an assignment or certificate belonging to another student.
  
  **The Fix for IDOR in `FileController`**:
  You must remove `/files/**` and `/uploads/**` from the `permitAll()` list in the Security Config.
  Then, inside the controller, you must ensure that the current user has permission to view this file (e.g., checking if the file belongs to them or to a student they are responsible for).
