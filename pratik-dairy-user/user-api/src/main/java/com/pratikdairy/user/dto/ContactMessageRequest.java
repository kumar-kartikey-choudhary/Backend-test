package com.pratikdairy.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ContactMessageRequest {

    @NotBlank(message = "Please tell us your name.")
    @Size(max = 100, message = "Your name is too long.")
    private String name;

    @NotBlank(message = "Please enter your email address.")
    @Email(message = "Enter a valid email address.")
    @Size(max = 100, message = "Your email address is too long.")
    private String email;

    @Pattern(regexp = "^[0-9+()\\- ]{0,20}$", message = "Enter a valid phone number.")
    private String phone;

    @NotBlank(message = "Please write your message.")
    @Size(min = 10, max = 2000, message = "Your message must be between 10 and 2000 characters.")
    private String message;
}
