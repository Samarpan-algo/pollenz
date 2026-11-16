package com.pollenz.common;

import java.util.stream.Collectors;

import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
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

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
		return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Malformed request body");
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
		return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Invalid value for " + ex.getName());
	}

	/**
	 * Optimistic-lock (@Version) conflicts and lock-acquisition failures. Pool writes take a row lock
	 * first, so this is a backstop rather than an expected path.
	 */
	@ExceptionHandler(ConcurrencyFailureException.class)
	public ResponseEntity<ApiError> handleConcurrency(ConcurrencyFailureException ex) {
		return build(HttpStatus.CONFLICT, "CONCURRENT_UPDATE", "The resource was changed concurrently; retry");
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiError> handleOther(Exception ex) {
		// Framework errors that already know their status (405, 404 for unknown routes, 415, ...).
		if (ex instanceof ErrorResponse er) {
			HttpStatus status = HttpStatus.resolve(er.getStatusCode().value());
			if (status != null && status.is4xxClientError()) {
				return build(status, status.name(), status.getReasonPhrase());
			}
		}
		// Log server-side only; never expose the stack trace or internal message to clients.
		org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class).error("Unhandled error", ex);
		return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected server error");
	}

	private ResponseEntity<ApiError> build(HttpStatus status, String code, String message) {
		return ResponseEntity.status(status)
				.body(new ApiError(status.value(), code, message));
	}

}
