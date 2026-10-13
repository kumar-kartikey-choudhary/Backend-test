package com.pratikdairy.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.ToString;

@Data
public class VerifyEmailRequest {
    @NotBlank(message = "The verification link is incomplete. Please request a new one.")
    @ToString.Exclude
    private String token;
}
