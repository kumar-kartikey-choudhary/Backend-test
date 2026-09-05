package com.pratikdairy.user.dto;

import com.pratikdairy.parent.base.dto.BaseDto;
import com.pratikdairy.user.enums.NotificationType;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class NotificationDto extends BaseDto {
    private NotificationType type;
    private String title;
    private String message;
    private boolean read;
    private String referenceId;
}