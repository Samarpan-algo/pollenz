package com.pollenz.chat;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ChatController {

	private final ChatService service;

	public ChatController(ChatService service) {
		this.service = service;
	}

	@GetMapping("/pools/{id}/messages")
	public List<ChatMessageResponse> history(@AuthenticationPrincipal Long userId, @PathVariable long id) {
		return service.history(userId, id);
	}

}
