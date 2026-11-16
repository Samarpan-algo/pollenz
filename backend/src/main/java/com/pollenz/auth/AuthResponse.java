package com.pollenz.auth;

import com.pollenz.user.UserResponse;

public record AuthResponse(String token, UserResponse user) {
}
