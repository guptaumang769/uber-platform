package com.umang.uber.rider.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RegisterRiderRequest(
        @NotBlank String name,
        @NotBlank @Email String email,
        String phone) {
}
