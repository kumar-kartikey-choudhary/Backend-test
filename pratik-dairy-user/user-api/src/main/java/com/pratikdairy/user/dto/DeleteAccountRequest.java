package com.pratikdairy.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.ToString;

@Data
public class DeleteAccountRequest {
    // Asked for again so a borrowed, unlocked device or a stolen session can't delete the account.
    @NotBlank(message = "Enter your password to confirm.")
    @ToString.Exclude
    private String password;
}
