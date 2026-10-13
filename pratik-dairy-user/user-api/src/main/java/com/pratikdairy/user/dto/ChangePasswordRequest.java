package com.pratikdairy.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.ToString;

@Data
public class ChangePasswordRequest {

    @NotBlank(message = "Your current password is required.")
    @ToString.Exclude
    private String currentPassword;

    @NotBlank(message = "A new password is required.")
    @ToString.Exclude
    private String newPassword;
}
