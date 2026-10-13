package com.pratikdairy.user.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.pratikdairy.parent.base.dto.BaseDto;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class UserDto extends BaseDto {

    private String firstName;
    private String middleName;
    private String lastName;
    private String username;
    private String email;
    private String phoneNumber;
    private String role;
    // Read-only: set by the server when the emailed link is used; anything sent by a client is ignored.
    private boolean emailVerified;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;
}