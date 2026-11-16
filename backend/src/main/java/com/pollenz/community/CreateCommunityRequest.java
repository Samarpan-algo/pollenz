package com.pollenz.community;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCommunityRequest(@NotBlank @Size(max = 100) String name) {
}
