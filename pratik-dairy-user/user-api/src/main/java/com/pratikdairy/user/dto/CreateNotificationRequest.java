package com.pratikdairy.user.dto;

import com.pratikdairy.user.enums.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateNotificationRequest {
    @NotBlank
    private String username;
    @NotNull
    private NotificationType type;
    @NotBlank
    private String title;
    private String message;
    private String referenceId;
}