// ─────────────────────────────────────────
// FILE: com/mtbs/dto/auth/LoginRequest.java
// ─────────────────────────────────────────
package com.mtbs.auth.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;

    private Long tenantId;

//    @NotBlank(message = "Tenant slug is required")
//    private String tenantSlug;
}