package com.pratikdairy.user.dto;

import com.pratikdairy.parent.base.dto.BaseDto;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AddressDto extends BaseDto {

    @NotBlank
    private String label;
    @NotBlank
    private String line1;
    private String line2;
    @NotBlank
    private String city;
    @NotBlank
    private String state;
    @NotBlank
    private String pincode;
    private String landmark;
    private Double latitude;
    private Double longitude;
    private boolean isDefault;
}