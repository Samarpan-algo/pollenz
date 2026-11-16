package com.pollenz;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Type;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import com.pollenz.pool.PoolExpiryJob;

import tools.jackson.databind.JsonNode;

/** Real STOMP client against a real server port. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "pollenz.scheduling.enabled=false")
class WebSocketIntegrationTest extends IntegrationTestBase {

	private static final long WAIT_SECONDS = 5;

	@LocalServerPort
	private int port;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PoolExpiryJob expiryJob;

	@Autowired
	private SimpleBrokerMessageHandler broker;

	@Autowired
	private SimpUserRegistry userRegistry;

	private final List<StompSession> sessions = new ArrayList<>();
	private WebSocketStompClient stomp;
	private ThreadPoolTaskScheduler scheduler;

	/** Everything a test session saw: ERROR frames and transport failures. */
	private static class Session extends StompSessionHandlerAdapter {

		final BlockingQueue<String> failures = new LinkedBlockingQueue<>();

		@Override
		public void handleFrame(StompHeaders headers, Object payload) {
			failures.add("ERROR frame: " + headers.getFirst("message"));
		}

		@Override
		public void handleException(StompSession session, org.springframework.messaging.simp.stomp.StompCommand command,
				StompHeaders headers, byte[] payload, Throwable ex) {
			failures.add("exception: " + ex);
		}

		@Override
		public void handleTransportError(StompSession session, Throwable ex) {
			failures.add("transport: " + ex);
		}

	}

	private record Connected(StompSession session, Session handler, long userId) {
	}

	private WebSocketStompClient client() {
		if (stomp == null) {
			stomp = new WebSocketStompClient(new StandardWebSocketClient());
			stomp.setMessageConverter(new JacksonJsonMessageConverter());
			scheduler = new ThreadPoolTaskScheduler();
			scheduler.initialize();
			stomp.setTaskScheduler(scheduler);
		}
		return stomp;
	}

	@AfterEach
	void closeSessions() {
		sessions.forEach(s -> {
			if (s.isConnected()) {
				s.disconnect();
			}
		});
		sessions.clear();
		if (stomp != null) {
			stomp.stop();
			scheduler.shutdown();
			stomp = null;
		}
	}

	private Connected connect(String authHeader, long userId) throws Exception {
		StompHeaders headers = new StompHeaders();
		if (authHeader != null) {
			headers.add("Authorization", authHeader);
		}
		Session handler = new Session();
		StompSession session = client()
				.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), headers, handler)
				.get(WAIT_SECONDS, TimeUnit.SECONDS);
		sessions.add(session);
		return new Connected(session, handler, userId);
	}

	private Connected connectAs(TestUser user) throws Exception {
		return connect("Bearer " + user.token(), user.id());
	}

	/** Subscribes and waits until the broker has registered the subscription; returns the event queue. */
	private BlockingQueue<JsonNode> subscribe(Connected conn, String destination) throws Exception {
		BlockingQueue<JsonNode> events = subscribeNoWait(conn.session(), destination);
		assertThat(awaitBrokerSubscription(conn.userId(), destination)).as("subscription " + destination).isTrue();
		return events;
	}

	private BlockingQueue<JsonNode> subscribeNoWait(StompSession session, String destination) {
		BlockingQueue<JsonNode> events = new LinkedBlockingQueue<>();
		session.subscribe(destination, new StompFrameHandler() {
			@Override
			public Type getPayloadType(StompHeaders headers) {
				return JsonNode.class;
			}

			@Override
			public void handleFrame(StompHeaders headers, Object payload) {
				events.add((JsonNode) payload);
			}
		});
		return events;
	}

	/**
	 * The broker registers a SUBSCRIBE asynchronously and the simple broker sends no receipt, so poll
	 * its registry. A "/user/queue/x" destination is registered as "/queue/x-user" + the session id.
	 */
	private boolean awaitBrokerSubscription(long userId, String destination) throws Exception {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
		while (System.nanoTime() < deadline) {
			if (hasSubscription(userId, destination)) {
				return true;
			}
			Thread.sleep(20);
		}
		return false;
	}

	private boolean hasSubscription(long userId, String destination) {
		if (!destination.startsWith("/user/")) {
			return brokerHas(destination);
		}
		SimpUser user = userRegistry.getUser(Long.toString(userId));
		if (user == null) {
			return false;
		}
		return user.getSessions().stream()
				.anyMatch(session -> brokerHas(destination.substring("/user".length()) + "-user" + session.getId()));
	}

	private boolean brokerHas(String destination) {
		SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
		accessor.setDestination(destination);
		Message<byte[]> probe = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
		return !broker.getSubscriptionRegistry().findSubscriptions(probe).isEmpty();
	}

	/** Polls until an event of the type arrives, skipping others; fails after the timeout. */
	private JsonNode awaitEvent(BlockingQueue<JsonNode> events, String type) throws Exception {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
		while (true) {
			long left = deadline - System.nanoTime();
			JsonNode event = left <= 0 ? null : events.poll(left, TimeUnit.NANOSECONDS);
			assertThat(event).as("event " + type).isNotNull();
			if (type.equals(event.get("type").asString())) {
				return event;
			}
		}
	}

	private void sendChat(StompSession session, long poolId, String body) {
		session.send("/app/pools/" + poolId + "/chat", Map.of("body", body));
	}

	private record Pool(TestUser host, TestUser member, TestUser outsider, long poolId) {
	}

	/** Host-only pool, one more community member (not yet in the pool) and one community outsider. */
	private Pool setup() throws Exception {
		TestUser host = registerUser("Host");
		TestUser member = registerUser("Member");
		TestUser outsider = registerUser("Outsider");
		JsonNode community = createCommunity(host.token(), "Block " + System.nanoTime());
		joinCommunity(member.token(), community);
		joinCommunity(outsider.token(), community);
		long poolId = createPool(host.token(), community.get("id").asLong(), "Fruit").get("id").asLong();
		return new Pool(host, member, outsider, poolId);
	}

	private static String topic(long poolId) {
		return "/topic/pools/" + poolId;
	}

	@Test
	void memberReceivesMemberJoinedAfterRestJoin() throws Exception {
		Pool p = setup();
		Connected host = connectAs(p.host());
		BlockingQueue<JsonNode> events = subscribe(host, topic(p.poolId()));

		joinPool(p.member().token(), p.poolId());

		JsonNode event = awaitEvent(events, "MEMBER_JOINED");
		assertThat(event.get("payload").get("poolId").asLong()).isEqualTo(p.poolId());
		assertThat(event.get("payload").get("actorId").asLong()).isEqualTo(p.member().id());
		assertThat(event.get("payload").has("hostUpiId")).isFalse();
	}

	@Test
	void lockPublishesPoolUpdatedWithStatus() throws Exception {
		Pool p = setup();
		joinPool(p.member().token(), p.poolId());
		Connected member = connectAs(p.member());
		BlockingQueue<JsonNode> events = subscribe(member, topic(p.poolId()));

		lockPool(p.host().token(), p.poolId());

		JsonNode event = awaitEvent(events, "POOL_UPDATED");
		assertThat(event.get("payload").get("status").asString()).isEqualTo("LOCKED");
		assertThat(event.get("payload").get("actorId").asLong()).isEqualTo(p.host().id());
	}

	@Test
	void itemChangeAndLeavePublishEvents() throws Exception {
		Pool p = setup();
		joinPool(p.member().token(), p.poolId());
		Connected host = connectAs(p.host());
		BlockingQueue<JsonNode> events = subscribe(host, topic(p.poolId()));

		addItem(p.member().token(), p.poolId(), "Apples", 2, 5000);
		assertThat(awaitEvent(events, "ITEMS_CHANGED").get("payload").get("actorId").asLong())
				.isEqualTo(p.member().id());

		deleteAuth("/api/pools/" + p.poolId() + "/leave", p.member().token());
		awaitEvent(events, "MEMBER_LEFT");
	}

	@Test
	void paymentAndSettlementPublishEvents() throws Exception {
		Pool p = setup();
		joinPool(p.member().token(), p.poolId());
		long itemId = addItem(p.member().token(), p.poolId(), "Milk", 1, 6000).get("id").asLong();
		lockPool(p.host().token(), p.poolId());
		order(p.host().token(), p.poolId(), orderBody(0, itemId, 6000));
		long memberId = memberIdOf(poolDetail(p.host().token(), p.poolId()), p.member().id());

		Connected host = connectAs(p.host());
		BlockingQueue<JsonNode> events = subscribe(host, topic(p.poolId()));

		markPaid(p.host().token(), p.poolId(), memberId);

		awaitEvent(events, "PAYMENT_UPDATED");
		JsonNode settled = awaitEvent(events, "POOL_UPDATED");
		assertThat(settled.get("payload").get("status").asString()).isEqualTo("SETTLED");
	}

	@Test
	void expiryJobPublishesPoolUpdated() throws Exception {
		TestUser host = registerUser("Host");
		JsonNode community = createCommunity(host.token(), "Block " + System.nanoTime());
		long poolId = createPool(host.token(), community.get("id").asLong(), "Soon").get("id").asLong();
		Connected conn = connectAs(host);
		BlockingQueue<JsonNode> events = subscribe(conn, topic(poolId));

		expiryJob.expireDue(Instant.now().plus(java.time.Duration.ofDays(2)));

		JsonNode event = awaitEvent(events, "POOL_UPDATED");
		assertThat(event.get("payload").get("status").asString()).isEqualTo("EXPIRED");
		assertThat(event.get("payload").has("actorId")).isFalse();
	}

	@Test
	void chatMessageReachesOtherMemberAndIsStored() throws Exception {
		Pool p = setup();
		joinPool(p.member().token(), p.poolId());
		Connected host = connectAs(p.host());
		Connected member = connectAs(p.member());
		BlockingQueue<JsonNode> hostEvents = subscribe(host, topic(p.poolId()));
		BlockingQueue<JsonNode> memberEvents = subscribe(member, topic(p.poolId()));

		sendChat(member.session(), p.poolId(), "  hello pool  ");

		JsonNode event = awaitEvent(hostEvents, "CHAT_MESSAGE");
		JsonNode payload = event.get("payload");
		assertThat(payload.get("body").asString()).isEqualTo("hello pool");
		assertThat(payload.get("senderId").asLong()).isEqualTo(p.member().id());
		assertThat(payload.get("senderName").asString()).isEqualTo("Member");
		assertThat(payload.get("poolId").asLong()).isEqualTo(p.poolId());
		assertThat(payload.get("createdAt").asString()).isNotBlank();
		awaitEvent(memberEvents, "CHAT_MESSAGE");

		Integer stored = jdbc.queryForObject(
				"select count(*) from messages where id = ? and body = 'hello pool' and sender_id = ?",
				Integer.class, payload.get("id").asLong(), p.member().id());
		assertThat(stored).isEqualTo(1);
		JsonNode history = json(getAuth("/api/pools/" + p.poolId() + "/messages", p.host().token()));
		assertThat(history.get(0).get("id").asLong()).isEqualTo(payload.get("id").asLong());
	}

	@Test
	void invalidChatMessageIsDroppedWithErrorOnUserQueueAndConnectionSurvives() throws Exception {
		Pool p = setup();
		Connected host = connectAs(p.host());
		BlockingQueue<JsonNode> events = subscribe(host, topic(p.poolId()));
		BlockingQueue<JsonNode> errors = subscribe(host, "/user/queue/errors");

		sendChat(host.session(), p.poolId(), "   ");
		sendChat(host.session(), p.poolId(), "x".repeat(1001));

		assertThat(errors.poll(WAIT_SECONDS, TimeUnit.SECONDS)).isNotNull();
		assertThat(errors.poll(WAIT_SECONDS, TimeUnit.SECONDS)).isNotNull();

		sendChat(host.session(), p.poolId(), "valid");
		JsonNode ok = awaitEvent(events, "CHAT_MESSAGE");
		assertThat(ok.get("payload").get("body").asString()).isEqualTo("valid");
		assertThat(host.session().isConnected()).isTrue();
		Integer count = jdbc.queryForObject("select count(*) from messages where pool_id = ?", Integer.class,
				p.poolId());
		assertThat(count).isEqualTo(1);
	}

	@Test
	void connectWithoutTokenFails() throws Exception {
		assertConnectRejected(null);
	}

	@Test
	void connectWithBadTokenFails() throws Exception {
		assertConnectRejected("Bearer not.a.jwt");
		assertConnectRejected("Basic abc");
	}

	private void assertConnectRejected(String authHeader) throws Exception {
		StompHeaders headers = new StompHeaders();
		if (authHeader != null) {
			headers.add("Authorization", authHeader);
		}
		Session handler = new Session();
		var future = client().connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), headers,
				handler);
		// Either the future fails or the server answers with an ERROR frame; it never yields a usable session.
		String failure = handler.failures.poll(WAIT_SECONDS, TimeUnit.SECONDS);
		assertThat(failure).as("connect must be rejected").isNotNull();
		assertThat(future.isDone() && !future.isCompletedExceptionally() && future.get().isConnected()).isFalse();
	}

	@Test
	void nonMemberCannotSubscribeToPoolTopic() throws Exception {
		Pool p = setup();
		Connected outsider = connectAs(p.outsider());
		subscribeNoWait(outsider.session(), topic(p.poolId()));
		assertThat(outsider.handler().failures.poll(WAIT_SECONDS, TimeUnit.SECONDS)).startsWith("ERROR frame");
	}

	@Test
	void nonMemberCannotSendChat() throws Exception {
		Pool p = setup();
		Connected outsider = connectAs(p.outsider());
		sendChat(outsider.session(), p.poolId(), "let me in");
		assertThat(outsider.handler().failures.poll(WAIT_SECONDS, TimeUnit.SECONDS)).startsWith("ERROR frame");
		Integer count = jdbc.queryForObject("select count(*) from messages where pool_id = ?", Integer.class,
				p.poolId());
		assertThat(count).isZero();
	}

	@Test
	void memberCannotUseOtherDestinations() throws Exception {
		Pool p = setup();
		Connected host = connectAs(p.host());
		subscribeNoWait(host.session(), "/topic/anything");
		assertThat(host.handler().failures.poll(WAIT_SECONDS, TimeUnit.SECONDS)).startsWith("ERROR frame");

		Connected host2 = connectAs(p.host());
		host2.session().send("/app/pools/" + p.poolId() + "/other", Map.of("body", "x"));
		assertThat(host2.handler().failures.poll(WAIT_SECONDS, TimeUnit.SECONDS)).startsWith("ERROR frame");
	}

}
