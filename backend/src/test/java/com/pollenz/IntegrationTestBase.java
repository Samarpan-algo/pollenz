package com.pollenz;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Shared setup for HTTP-level integration tests. Every test registers its own users with unique
 * emails, so tests do not depend on run order or on each other's data.
 * Scheduled jobs are off here; tests call job methods directly. The application clock is a
 * {@link MutableClock}, reset to the system clock after every test.
 */
@SpringBootTest(properties = "pollenz.scheduling.enabled=false")
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, TestClockConfiguration.class })
public abstract class IntegrationTestBase {

	protected static final String PASSWORD = "password123";

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Autowired
	protected MockMvc mvc;

	@Autowired
	protected MutableClock clock;

	@AfterEach
	void resetClock() {
		clock.reset();
	}

	/** A registered user: JWT plus user id. */
	protected record TestUser(String token, long id) {
	}

	protected static String uniqueEmail() {
		return "user-" + UUID.randomUUID() + "@example.com";
	}

	protected static JsonNode json(ResultActions result) throws Exception {
		return JSON.readTree(result.andReturn().getResponse().getContentAsString());
	}

	protected static int statusOf(ResultActions result) {
		return result.andReturn().getResponse().getStatus();
	}

	/** The {@code error} code of an error response. */
	protected static String errorOf(ResultActions result) throws Exception {
		return json(result).get("error").asString();
	}

	protected ResultActions postJson(String path, String token, String body) throws Exception {
		var req = post(path).contentType(MediaType.APPLICATION_JSON).content(body);
		if (token != null) {
			req.header("Authorization", "Bearer " + token);
		}
		return mvc.perform(req);
	}

	/** POST without a body. */
	protected ResultActions postAuth(String path, String token) throws Exception {
		return mvc.perform(post(path).header("Authorization", "Bearer " + token));
	}

	protected ResultActions putJson(String path, String token, String body) throws Exception {
		return mvc.perform(put(path).header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	protected ResultActions getAuth(String path, String token) throws Exception {
		return mvc.perform(get(path).header("Authorization", "Bearer " + token));
	}

	protected ResultActions deleteAuth(String path, String token) throws Exception {
		return mvc.perform(delete(path).header("Authorization", "Bearer " + token));
	}

	/** Registers a fresh user and returns the JWT. */
	protected String registerToken(String name) throws Exception {
		return registerToken(name, uniqueEmail());
	}

	protected String registerToken(String name, String email) throws Exception {
		ResultActions res = postJson("/api/auth/register", null, registerBody(name, email, PASSWORD))
				.andExpect(status().isCreated());
		return json(res).get("token").asString();
	}

	/** Registers a fresh user and returns token and id. */
	protected TestUser registerUser(String name) throws Exception {
		JsonNode res = json(postJson("/api/auth/register", null, registerBody(name, uniqueEmail(), PASSWORD))
				.andExpect(status().isCreated()));
		return new TestUser(res.get("token").asString(), res.get("user").get("id").asLong());
	}

	protected static String registerBody(String name, String email, String password) {
		return """
				{"name":"%s","email":"%s","password":"%s"}""".formatted(name, email, password);
	}

	/** Creates a community owned by the token's user and returns the response JSON. */
	protected JsonNode createCommunity(String token, String name) throws Exception {
		return json(postJson("/api/communities", token, """
				{"name":"%s"}""".formatted(name)).andExpect(status().isCreated()));
	}

	protected void joinCommunity(String token, JsonNode community) throws Exception {
		postJson("/api/communities/join", token, """
				{"inviteCode":"%s"}""".formatted(community.get("inviteCode").asString()))
				.andExpect(status().isOk());
	}

	// ---- pools ----

	protected static String poolBody(String platform, String title, long targetAmountPaise, Instant closesAt,
			String pickupSpot) {
		return """
				{"platform":"%s","title":"%s","targetAmountPaise":%d,"closesAt":"%s","pickupSpot":"%s"}"""
				.formatted(platform, title, targetAmountPaise, closesAt, pickupSpot);
	}

	/** A valid pool body closing in one day. */
	protected static String poolBody(String title) {
		return poolBody("BLINKIT", title, 19900, Instant.now().plus(Duration.ofDays(1)), "Gate 2");
	}

	/** Creates an OPEN pool hosted by the token's user and returns the detail JSON. */
	protected JsonNode createPool(String token, long communityId, String title) throws Exception {
		return json(postJson("/api/communities/" + communityId + "/pools", token, poolBody(title))
				.andExpect(status().isCreated()));
	}

	protected void joinPool(String token, long poolId) throws Exception {
		postAuth("/api/pools/" + poolId + "/join", token).andExpect(status().isOk());
	}

	protected static String itemBody(String name, int quantity, long estPricePaise) {
		return """
				{"name":"%s","quantity":%d,"estPricePaise":%d}""".formatted(name, quantity, estPricePaise);
	}

	/** Adds an item for the token's user and returns the item JSON. */
	protected JsonNode addItem(String token, long poolId, String name, int quantity, long estPricePaise)
			throws Exception {
		return json(postJson("/api/pools/" + poolId + "/items", token, itemBody(name, quantity, estPricePaise))
				.andExpect(status().isCreated()));
	}

	protected JsonNode poolDetail(String token, long poolId) throws Exception {
		return json(getAuth("/api/pools/" + poolId, token).andExpect(status().isOk()));
	}

	protected void lockPool(String hostToken, long poolId) throws Exception {
		postAuth("/api/pools/" + poolId + "/lock", hostToken).andExpect(status().isOk());
	}

	/** Body of the order action: {@code itemPrices} is pairs of item id and final price. */
	protected static String orderBody(long feesPaise, long... itemPrices) {
		StringBuilder items = new StringBuilder();
		for (int i = 0; i < itemPrices.length; i += 2) {
			if (i > 0) {
				items.append(',');
			}
			items.append("""
					{"itemId":%d,"finalPricePaise":%d}""".formatted(itemPrices[i], itemPrices[i + 1]));
		}
		return """
				{"feesPaise":%d,"items":[%s]}""".formatted(feesPaise, items);
	}

	protected ResultActions order(String hostToken, long poolId, String body) throws Exception {
		return postJson("/api/pools/" + poolId + "/order", hostToken, body);
	}

	protected ResultActions markPaid(String hostToken, long poolId, long memberId) throws Exception {
		return postAuth("/api/pools/" + poolId + "/members/" + memberId + "/paid", hostToken);
	}

	/** The pool_members id of a user in a pool detail. */
	protected static long memberIdOf(JsonNode detail, long userId) {
		for (JsonNode m : detail.get("members")) {
			if (m.get("userId").asLong() == userId) {
				return m.get("memberId").asLong();
			}
		}
		throw new AssertionError("User " + userId + " is not in pool " + detail.get("id"));
	}

	// ---- concurrency ----

	/**
	 * Runs all calls at once: each waits on a shared start gate so they hit the server together.
	 * Returns the results in call order. Fails if any call throws or the batch takes over 60 s.
	 */
	protected static <T> List<T> concurrently(List<Callable<T>> calls) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(calls.size());
		try {
			CountDownLatch ready = new CountDownLatch(calls.size());
			CountDownLatch start = new CountDownLatch(1);
			List<Future<T>> futures = new ArrayList<>();
			for (Callable<T> call : calls) {
				futures.add(pool.submit(() -> {
					ready.countDown();
					start.await();
					return call.call();
				}));
			}
			ready.await(10, TimeUnit.SECONDS);
			start.countDown();
			List<T> results = new ArrayList<>();
			for (Future<T> f : futures) {
				results.add(f.get(60, TimeUnit.SECONDS));
			}
			return results;
		}
		finally {
			pool.shutdownNow();
		}
	}

}
