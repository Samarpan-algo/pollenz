package com.pollenz.common;

public record ApiError(int status, String error, String message) {
}
