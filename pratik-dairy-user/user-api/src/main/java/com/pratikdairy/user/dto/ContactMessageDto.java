package com.pratikdairy.user.dto;

import com.pratikdairy.parent.base.dto.BaseDto;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ContactMessageDto extends BaseDto {
    private String name;
    private String email;
    private String phone;
    private String message;
    private boolean handled;
}
