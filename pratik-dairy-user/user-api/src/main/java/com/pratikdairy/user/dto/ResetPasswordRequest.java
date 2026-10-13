package com.pratikdairy.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.ToString;

@Data
public class ResetPasswordRequest {

    @NotBlank(message = "The reset link is incomplete. Please request a new one.")
    @ToString.Exclude
    private String token;

    @NotBlank(message = "A new password is required.")
    @ToString.Exclude
    private String newPassword;
}
