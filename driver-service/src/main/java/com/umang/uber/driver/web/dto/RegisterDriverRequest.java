package com.umang.uber.driver.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RegisterDriverRequest(
        @NotBlank String name,
        @NotBlank @Email String email,
        String vehicle) {
}
