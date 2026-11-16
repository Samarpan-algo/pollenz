package com.pollenz.pool;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class PoolController {

	private final PoolService service;

	public PoolController(PoolService service) {
		this.service = service;
	}

	@PostMapping("/communities/{id}/pools")
	@ResponseStatus(HttpStatus.CREATED)
	public PoolDetailResponse create(@AuthenticationPrincipal Long userId, @PathVariable long id,
			@Valid @RequestBody CreatePoolRequest req) {
		return service.create(userId, id, req);
	}

	@GetMapping("/communities/{id}/pools")
	public List<PoolSummary> list(@AuthenticationPrincipal Long userId, @PathVariable long id,
			@RequestParam(required = false) PoolStatus status) {
		return service.list(userId, id, status);
	}

	@GetMapping("/pools/{id}")
	public PoolDetailResponse detail(@AuthenticationPrincipal Long userId, @PathVariable long id) {
		return service.get(userId, id);
	}

	@PostMapping("/pools/{id}/join")
	public PoolDetailResponse join(@AuthenticationPrincipal Long userId, @PathVariable long id) {
		return service.join(userId, id);
	}

	@DeleteMapping("/pools/{id}/leave")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void leave(@AuthenticationPrincipal Long userId, @PathVariable long id) {
		service.leave(userId, id);
	}

	@PostMapping("/pools/{id}/items")
	@ResponseStatus(HttpStatus.CREATED)
	public PoolItemResponse addItem(@AuthenticationPrincipal Long userId, @PathVariable long id,
			@Valid @RequestBody ItemRequest req) {
		return service.addItem(userId, id, req);
	}

	@PutMapping("/pools/{id}/items/{itemId}")
	public PoolItemResponse updateItem(@AuthenticationPrincipal Long userId, @PathVariable long id,
			@PathVariable long itemId, @Valid @RequestBody ItemRequest req) {
		return service.updateItem(userId, id, itemId, req);
	}

	@DeleteMapping("/pools/{id}/items/{itemId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteItem(@AuthenticationPrincipal Long userId, @PathVariable long id,
			@PathVariable long itemId) {
		service.deleteItem(userId, id, itemId);
	}

	@PostMapping("/pools/{id}/lock")
	public PoolDetailResponse lock(@AuthenticationPrincipal Long userId, @PathVariable long id) {
		return service.lock(userId, id);
	}

	@PostMapping("/pools/{id}/cancel")
	public PoolDetailResponse cancel(@AuthenticationPrincipal Long userId, @PathVariable long id) {
		return service.cancel(userId, id);
	}

	@PostMapping("/pools/{id}/order")
	public PoolDetailResponse order(@AuthenticationPrincipal Long userId, @PathVariable long id,
			@Valid @RequestBody OrderRequest req) {
		return service.order(userId, id, req);
	}

	@PostMapping("/pools/{id}/deliver")
	public PoolDetailResponse deliver(@AuthenticationPrincipal Long userId, @PathVariable long id) {
		return service.deliver(userId, id);
	}

	@PostMapping("/pools/{id}/members/{memberId}/paid")
	public PoolDetailResponse markPaid(@AuthenticationPrincipal Long userId, @PathVariable long id,
			@PathVariable long memberId) {
		return service.markMemberPaid(userId, id, memberId);
	}

}
