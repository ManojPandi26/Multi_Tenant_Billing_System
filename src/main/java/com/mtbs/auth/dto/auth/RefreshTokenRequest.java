package com.mtbs.auth.dto.auth;

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
public class RefreshTokenRequest {

    // Optional — if absent, token is read from HttpOnly cookie automatically
    private String refreshToken;

    private Long tenantId;
}