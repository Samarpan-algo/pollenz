package com.pollenz.chat;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pollenz.common.ApiException;
import com.pollenz.community.CommunityService;
import com.pollenz.pool.Pool;
import com.pollenz.pool.PoolMemberRepository;
import com.pollenz.pool.PoolRepository;
import com.pollenz.user.UserRepository;

/** Pool chat: history and posting. Chat is allowed in every pool status. */
@Service
public class ChatService {

	static final int MAX_BODY_LENGTH = 1000;
	static final int HISTORY_LIMIT = 200;

	private final MessageRepository messages;
	private final PoolRepository pools;
	private final PoolMemberRepository poolMembers;
	private final CommunityService communityService;
	private final UserRepository users;
	private final Clock clock;

	public ChatService(MessageRepository messages, PoolRepository pools, PoolMemberRepository poolMembers,
			CommunityService communityService, UserRepository users, Clock clock) {
		this.messages = messages;
		this.pools = pools;
		this.poolMembers = poolMembers;
		this.communityService = communityService;
		this.users = users;
		this.clock = clock;
	}

	/** The latest 200 messages, oldest first. 404 POOL_NOT_FOUND, 403 NOT_A_MEMBER, 403 NOT_IN_POOL. */
	@Transactional(readOnly = true)
	public List<ChatMessageResponse> history(long userId, long poolId) {
		Pool pool = pools.findById(poolId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "POOL_NOT_FOUND", "Pool not found"));
		communityService.requireMembership(userId, pool.getCommunityId());
		requirePoolMember(userId, poolId);
		List<ChatMessageResponse> latest = new ArrayList<>(messages.findLatest(poolId, Limit.of(HISTORY_LIMIT)));
		Collections.reverse(latest);
		return latest;
	}

	/** Validates and stores a message from a pool member. 403 NOT_IN_POOL, then 400 VALIDATION_FAILED. */
	@Transactional
	public ChatMessageResponse post(long userId, long poolId, String rawBody) {
		requirePoolMember(userId, poolId);
		String body = rawBody == null ? "" : rawBody.trim();
		if (body.isEmpty() || body.length() > MAX_BODY_LENGTH) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
					"Message must be 1 to " + MAX_BODY_LENGTH + " characters");
		}
		Message saved = messages.save(new Message(poolId, userId, body, clock.instant()));
		String senderName = users.findById(userId).orElseThrow().getName();
		return new ChatMessageResponse(saved.getId(), poolId, userId, senderName, saved.getBody(),
				saved.getCreatedAt());
	}

	/** Used by the STOMP interceptor; false for unknown pools too. */
	public boolean isPoolMember(long userId, long poolId) {
		return poolMembers.existsByPoolIdAndUserId(poolId, userId);
	}

	private void requirePoolMember(long userId, long poolId) {
		if (!isPoolMember(userId, poolId)) {
			throw new ApiException(HttpStatus.FORBIDDEN, "NOT_IN_POOL", "You are not a member of this pool");
		}
	}

}
