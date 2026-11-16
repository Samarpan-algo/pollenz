package com.pollenz.common;

import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<ApiError> handleApiException(ApiException ex) {
		return build(ex.getStatus(), ex.getCode(), ex.getMessage());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
		String fields = ex.getBindingResult().getFieldErrors().stream()
				.map(FieldError::getField)
				.distinct()
				.collect(Collectors.joining(", "));
		return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Invalid fields: " + fields);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiError> handleOther(Exception ex) {
		// Log server-side only; never expose the stack trace or internal message to clients.
		org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class).error("Unhandled error", ex);
		return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected server error");
	}

	private ResponseEntity<ApiError> build(HttpStatus status, String code, String message) {
		return ResponseEntity.status(status)
				.body(new ApiError(status.value(), code, message));
	}

}
