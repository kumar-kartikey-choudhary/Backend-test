package com.pratikdairy.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ForgotPasswordRequest {

    @NotBlank(message = "Enter the email address of your account.")
    @Email(message = "Enter a valid email address.")
    private String email;
}
