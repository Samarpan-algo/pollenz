package com.pollenz.pool;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pollenz.common.ApiException;
import com.pollenz.community.CommunityRepository;
import com.pollenz.community.CommunityService;
import com.pollenz.user.User;
import com.pollenz.user.UserRepository;

/**
 * Pools, their members and items.
 *
 * Concurrency strategy: every write to a pool, its members or its items (join, leave, item changes,
 * lock, cancel, the expiry job) first loads the pool row with {@link #lockPool} (PESSIMISTIC_WRITE,
 * i.e. SELECT ... FOR UPDATE) and only then checks status, membership and ownership. Writes to one
 * pool are therefore serialized: a join or item change can never pass the OPEN check against a pool
 * that a concurrent transaction has locked, cancelled or expired, and of two concurrent transitions
 * the second sees the first one's result and gets 409 INVALID_TRANSITION. Each transaction locks a
 * single pool row, so there is no lock-ordering deadlock. Backstops: the {@code @Version} column is
 * still bumped on every pool update (an optimistic-lock failure from a path that skipped the lock
 * becomes 409 CONCURRENT_UPDATE), and the (pool_id, user_id) unique constraint rejects double joins.
 */
@Service
public class PoolService {

	private final PoolRepository pools;
	private final PoolMemberRepository members;
	private final PoolItemRepository items;
	private final CommunityRepository communities;
	private final CommunityService communityService;
	private final UserRepository users;

	public PoolService(PoolRepository pools, PoolMemberRepository members, PoolItemRepository items,
			CommunityRepository communities, CommunityService communityService, UserRepository users) {
		this.pools = pools;
		this.members = members;
		this.items = items;
		this.communities = communities;
		this.communityService = communityService;
		this.users = users;
	}

	// ---- pools -------------------------------------------------------------------------------

	@Transactional
	public PoolDetailResponse create(long userId, long communityId, CreatePoolRequest req) {
		requireCommunityMember(userId, communityId);
		Pool pool = pools.save(new Pool(communityId, userId, req.platform(), req.title().trim(),
				req.targetAmountPaise(), req.closesAt(), req.pickupSpot().trim(), Instant.now()));
		members.save(new PoolMember(pool.getId(), userId));
		return detail(pool);
	}

	@Transactional(readOnly = true)
	public List<PoolSummary> list(long userId, long communityId, PoolStatus status) {
		requireCommunityMember(userId, communityId);
		return pools.findSummaries(communityId, status);
	}

	@Transactional(readOnly = true)
	public PoolDetailResponse get(long userId, long poolId) {
		Pool pool = pools.findById(poolId).orElseThrow(PoolService::poolNotFound);
		communityService.requireMembership(userId, pool.getCommunityId());
		return detail(pool);
	}

	// ---- membership --------------------------------------------------------------------------

	@Transactional
	public PoolDetailResponse join(long userId, long poolId) {
		Pool pool = lockPool(poolId);
		communityService.requireMembership(userId, pool.getCommunityId());
		pool.requireOpen();
		if (members.existsByPoolIdAndUserId(poolId, userId)) {
			throw alreadyInPool();
		}
		try {
			members.saveAndFlush(new PoolMember(poolId, userId));
		}
		catch (DataIntegrityViolationException ex) {
			// Final guard: the (pool_id, user_id) unique constraint.
			throw alreadyInPool();
		}
		return detail(pool);
	}

	@Transactional
	public void leave(long userId, long poolId) {
		Pool pool = lockPool(poolId);
		communityService.requireMembership(userId, pool.getCommunityId());
		PoolMember member = requirePoolMember(poolId, userId);
		if (pool.isHost(userId)) {
			throw new ApiException(HttpStatus.CONFLICT, "HOST_CANNOT_LEAVE",
					"The host cannot leave the pool; cancel it instead");
		}
		pool.requireOpen();
		items.deleteByPoolMemberId(member.getId());
		members.delete(member);
	}

	// ---- items -------------------------------------------------------------------------------

	@Transactional
	public PoolItemResponse addItem(long userId, long poolId, ItemRequest req) {
		PoolMember member = lockOpenPoolForMember(userId, poolId);
		PoolItem item = items.save(new PoolItem(member.getId(), req.name().trim(), req.quantity(),
				req.estPricePaise()));
		return PoolItemResponse.from(item);
	}

	@Transactional
	public PoolItemResponse updateItem(long userId, long poolId, long itemId, ItemRequest req) {
		PoolItem item = requireOwnItem(lockOpenPoolForMember(userId, poolId), itemId);
		item.update(req.name().trim(), req.quantity(), req.estPricePaise());
		return PoolItemResponse.from(item);
	}

	@Transactional
	public void deleteItem(long userId, long poolId, long itemId) {
		items.delete(requireOwnItem(lockOpenPoolForMember(userId, poolId), itemId));
	}

	// ---- lifecycle ---------------------------------------------------------------------------

	@Transactional
	public PoolDetailResponse lock(long userId, long poolId) {
		return hostTransition(userId, poolId, PoolTransition.LOCK);
	}

	@Transactional
	public PoolDetailResponse cancel(long userId, long poolId) {
		return hostTransition(userId, poolId, PoolTransition.CANCEL);
	}

	/**
	 * Expiry of one pool, in its own transaction (called per pool by {@link PoolExpiryJob}). Re-checks
	 * under the row lock that the pool is still OPEN and due, so a pool the host locked or cancelled
	 * a moment earlier is skipped. Returns the new status, or empty if nothing changed.
	 */
	@Transactional
	public Optional<PoolStatus> expireIfDue(long poolId, Instant now) {
		Optional<Pool> found = pools.findByIdForUpdate(poolId);
		if (found.isEmpty()) {
			return Optional.empty();
		}
		Pool pool = found.get();
		if (pool.getStatus() != PoolStatus.OPEN || pool.getClosesAt().isAfter(now)) {
			return Optional.empty();
		}
		pool.apply(members.countByPoolId(poolId) >= 2 ? PoolTransition.LOCK : PoolTransition.EXPIRE);
		pools.flush();
		return Optional.of(pool.getStatus());
	}

	// ---- helpers -----------------------------------------------------------------------------

	/**
	 * Loads the pool with a row lock held until the transaction commits; 404 POOL_NOT_FOUND if missing.
	 * Must be the first load of this pool in the transaction (see {@link PoolRepository#findByIdForUpdate}).
	 */
	Pool lockPool(long poolId) {
		return pools.findByIdForUpdate(poolId).orElseThrow(PoolService::poolNotFound);
	}

	private PoolDetailResponse hostTransition(long userId, long poolId, PoolTransition transition) {
		Pool pool = lockPool(poolId);
		communityService.requireMembership(userId, pool.getCommunityId());
		if (!pool.isHost(userId)) {
			throw new ApiException(HttpStatus.FORBIDDEN, "NOT_HOST", "Only the host can do this");
		}
		pool.apply(transition);
		pools.flush();
		return detail(pool);
	}

	/** Lock the pool, then require community member, pool member and OPEN, in that order. */
	private PoolMember lockOpenPoolForMember(long userId, long poolId) {
		Pool pool = lockPool(poolId);
		communityService.requireMembership(userId, pool.getCommunityId());
		PoolMember member = requirePoolMember(poolId, userId);
		pool.requireOpen();
		return member;
	}

	private PoolMember requirePoolMember(long poolId, long userId) {
		return members.findByPoolIdAndUserId(poolId, userId)
				.orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "NOT_IN_POOL",
						"You are not a member of this pool"));
	}

	private PoolItem requireOwnItem(PoolMember member, long itemId) {
		PoolItem item = items.findInPool(member.getPoolId(), itemId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ITEM_NOT_FOUND",
						"No such item in this pool"));
		if (!item.getPoolMemberId().equals(member.getId())) {
			throw new ApiException(HttpStatus.FORBIDDEN, "NOT_YOUR_ITEM", "You can only change your own items");
		}
		return item;
	}

	/** 404 COMMUNITY_NOT_FOUND if the community does not exist, else 403 NOT_A_MEMBER for outsiders. */
	private void requireCommunityMember(long userId, long communityId) {
		if (!communities.existsById(communityId)) {
			throw new ApiException(HttpStatus.NOT_FOUND, "COMMUNITY_NOT_FOUND", "Community not found");
		}
		communityService.requireMembership(userId, communityId);
	}

	/** Builds the detail view with a fixed number of queries: members, their users, all items. */
	private PoolDetailResponse detail(Pool pool) {
		List<PoolMember> poolMembers = members.findByPoolIdOrderById(pool.getId());
		Map<Long, User> userById = users.findAllById(poolMembers.stream().map(PoolMember::getUserId).toList())
				.stream().collect(Collectors.toMap(User::getId, Function.identity()));
		User host = userById.get(pool.getHostId());
		if (host == null) {
			// The host is always a member; this only guards against inconsistent data.
			host = users.findById(pool.getHostId()).orElseThrow();
		}
		List<PoolItem> poolItems = items.findByPoolId(pool.getId());
		Map<Long, List<PoolItemResponse>> itemsByMember = poolItems.stream().collect(Collectors.groupingBy(
				PoolItem::getPoolMemberId, Collectors.mapping(PoolItemResponse::from, Collectors.toList())));
		long current = poolItems.stream().mapToLong(PoolItem::getEstPricePaise).sum();

		List<PoolMemberResponse> memberViews = poolMembers.stream()
				.map(m -> new PoolMemberResponse(m.getId(), m.getUserId(), userById.get(m.getUserId()).getName(),
						m.getSharePaise(), m.isPaid(), m.getPaidAt(), itemsByMember.getOrDefault(m.getId(), List.of())))
				.toList();
		return new PoolDetailResponse(pool.getId(), pool.getCommunityId(), pool.getPlatform(), pool.getTitle(),
				pool.getTargetAmountPaise(), current, pool.getClosesAt(), pool.getPickupSpot(), pool.getStatus(),
				pool.getFeesPaise(), host.getId(), host.getName(), host.getUpiId(), pool.getCreatedAt(), memberViews);
	}

	private static ApiException poolNotFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "POOL_NOT_FOUND", "Pool not found");
	}

	private static ApiException alreadyInPool() {
		return new ApiException(HttpStatus.CONFLICT, "ALREADY_IN_POOL", "You are already in this pool");
	}

}
