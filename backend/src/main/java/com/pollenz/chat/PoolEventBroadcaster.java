package com.pollenz.chat;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.pollenz.pool.PoolEvent;

/**
 * Forwards pool domain events to the STOMP broker, only after the publishing transaction has
 * committed, so a client that refetches never reads stale data and rolled-back changes are never
 * announced.
 */
@Component
public class PoolEventBroadcaster {

	private final SimpMessagingTemplate template;

	public PoolEventBroadcaster(SimpMessagingTemplate template) {
		this.template = template;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onPoolEvent(PoolEvent event) {
		template.convertAndSend(topic(event.poolId()), new RealtimeEvent(event.type().name(),
				new PoolEventPayload(event.poolId(), event.actorId(), event.status())));
	}

	public void chatMessage(ChatMessageResponse message) {
		template.convertAndSend(topic(message.poolId()), new RealtimeEvent("CHAT_MESSAGE", message));
	}

	static String topic(long poolId) {
		return "/topic/pools/" + poolId;
	}

}
