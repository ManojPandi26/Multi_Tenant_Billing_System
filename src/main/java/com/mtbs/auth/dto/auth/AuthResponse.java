package com.mtbs.auth.dto.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthResponse {

    // Intentionally nullable — null in cookie-based auth flow (tokens live in HttpOnly cookies).
    // Populated only for API-client / mobile flows that read tokens from the response body.
    private String accessToken;
    private String refreshToken;

    private Long userId;
    private String email;
    private String name;
    private String role;
    private Long tenantId;
    private String schemaName;

    private Boolean onboardingComplete;
    private Integer onboardingStep;
}