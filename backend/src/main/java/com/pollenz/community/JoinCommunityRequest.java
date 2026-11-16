package com.pollenz.community;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JoinCommunityRequest(@NotBlank @Size(max = 32) String inviteCode) {
}
