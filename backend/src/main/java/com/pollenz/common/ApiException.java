package com.pollenz.common;

import org.springframework.http.HttpStatus;

/**
 * Domain error carrying an HTTP status and a stable error code (e.g. POOL_NOT_OPEN).
 */
public class ApiException extends RuntimeException {

	private final HttpStatus status;
	private final String code;

	public ApiException(HttpStatus status, String code, String message) {
		super(message);
		this.status = status;
		this.code = code;
	}

	public HttpStatus getStatus() {
		return status;
	}

	public String getCode() {
		return code;
	}

}
