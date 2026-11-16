package com.pollenz.community;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pollenz.common.ApiException;

@Service
public class CommunityService {

	private static final String CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
	private static final int CODE_LENGTH = 8;
	private static final int MAX_CODE_ATTEMPTS = 10;

	private final CommunityRepository communities;
	private final MembershipRepository memberships;
	private final SecureRandom random = new SecureRandom();

	public CommunityService(CommunityRepository communities, MembershipRepository memberships) {
		this.communities = communities;
		this.memberships = memberships;
	}

	@Transactional
	public CommunitySummary create(long userId, CreateCommunityRequest req) {
		Instant now = Instant.now();
		Community community = communities.save(
				new Community(req.name().trim(), uniqueInviteCode(), userId, now));
		memberships.save(new Membership(userId, community.getId(), Role.ADMIN, now));
		return summary(community, Role.ADMIN);
	}

	@Transactional
	public CommunitySummary join(long userId, JoinCommunityRequest req) {
		String code = req.inviteCode().trim().toUpperCase(Locale.ROOT);
		Community community = communities.findByInviteCode(code)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COMMUNITY_NOT_FOUND",
						"No community with that invite code"));
		if (memberships.existsByUserIdAndCommunityId(userId, community.getId())) {
			throw alreadyMember();
		}
		try {
			memberships.saveAndFlush(new Membership(userId, community.getId(), Role.MEMBER, Instant.now()));
		}
		catch (DataIntegrityViolationException ex) {
			// Concurrent double join: the (user_id, community_id) unique constraint is the final guard.
			throw alreadyMember();
		}
		return summary(community, Role.MEMBER);
	}

	@Transactional(readOnly = true)
	public List<CommunitySummary> listForUser(long userId) {
		return memberships.findSummariesByUserId(userId);
	}

	@Transactional(readOnly = true)
	public CommunityDetailResponse detail(long userId, long communityId) {
		Community community = communities.findById(communityId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COMMUNITY_NOT_FOUND",
						"Community not found"));
		requireMembership(userId, communityId);
		return new CommunityDetailResponse(community.getId(), community.getName(), community.getInviteCode(),
				community.getCreatedAt(), memberships.findMembers(communityId));
	}

	/**
	 * Throws 403 NOT_A_MEMBER unless the user belongs to the community. Reused by later phases.
	 * Does not check that the community exists: a non-member of a missing community also gets 403.
	 */
	@Transactional(readOnly = true)
	public Membership requireMembership(long userId, long communityId) {
		return memberships.findByUserIdAndCommunityId(userId, communityId)
				.orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "NOT_A_MEMBER",
						"You are not a member of this community"));
	}

	private String uniqueInviteCode() {
		for (int i = 0; i < MAX_CODE_ATTEMPTS; i++) {
			StringBuilder sb = new StringBuilder(CODE_LENGTH);
			for (int j = 0; j < CODE_LENGTH; j++) {
				sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
			}
			String code = sb.toString();
			if (!communities.existsByInviteCode(code)) {
				return code;
			}
		}
		throw new IllegalStateException("Could not generate a unique invite code");
	}

	private static CommunitySummary summary(Community c, Role role) {
		return new CommunitySummary(c.getId(), c.getName(), c.getInviteCode(), role, c.getCreatedAt());
	}

	private static ApiException alreadyMember() {
		return new ApiException(HttpStatus.CONFLICT, "ALREADY_MEMBER", "You are already a member of this community");
	}

}
