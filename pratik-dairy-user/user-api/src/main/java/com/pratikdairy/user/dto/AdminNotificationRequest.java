package com.pratikdairy.user.dto;

import com.pratikdairy.user.enums.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminNotificationRequest {

    public enum Audience { USER, ALL_CUSTOMERS }

    @NotNull(message = "Choose who the notification is for.")
    private Audience audience;

    // Required when audience is USER.
    private String username;

    private NotificationType type = NotificationType.SYSTEM;

    @NotBlank(message = "A title is required.")
    @Size(max = 150, message = "The title can be at most 150 characters.")
    private String title;

    @Size(max = 1000, message = "The message can be at most 1000 characters.")
    private String message;
}
