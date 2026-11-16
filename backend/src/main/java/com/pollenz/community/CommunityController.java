package com.pollenz.community;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/communities")
public class CommunityController {

	private final CommunityService service;

	public CommunityController(CommunityService service) {
		this.service = service;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public CommunitySummary create(@AuthenticationPrincipal Long userId,
			@Valid @RequestBody CreateCommunityRequest req) {
		return service.create(userId, req);
	}

	@PostMapping("/join")
	public CommunitySummary join(@AuthenticationPrincipal Long userId,
			@Valid @RequestBody JoinCommunityRequest req) {
		return service.join(userId, req);
	}

	@GetMapping
	public List<CommunitySummary> list(@AuthenticationPrincipal Long userId) {
		return service.listForUser(userId);
	}

	@GetMapping("/{id}")
	public CommunityDetailResponse detail(@AuthenticationPrincipal Long userId, @PathVariable long id) {
		return service.detail(userId, id);
	}

}
