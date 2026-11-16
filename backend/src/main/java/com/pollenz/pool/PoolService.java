package com.pollenz.pool;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
 * Pools, their members and items, the bill split and payment tracking.
 *
 * Concurrency strategy: every write to a pool, its members or its items (join, leave, item changes,
 * lock, cancel, order, deliver, paid, the expiry job) first loads the pool row with {@link #lockPool} (PESSIMISTIC_WRITE,
 * i.e. SELECT ... FOR UPDATE) and only then checks status, membership and ownership. Writes to one
 * pool are therefore serialized: a join or item change can never pass the OPEN check against a pool
 * that a concurrent transaction has locked, cancelled or expired, and of two concurrent transitions
 * the second sees the first one's result and gets 409 INVALID_TRANSITION. Each transaction locks a
 * single pool row, so there is no lock-ordering deadlock. Backstops: the {@code @Version} column is
 * still bumped on every pool update (an optimistic-lock failure from a path that skipped the lock
 * becomes 409 CONCURRENT_UPDATE), and the (pool_id, user_id) unique constraint rejects double joins.
 * Because "paid" calls are serialized too, exactly one of them sees every member paid and settles.
 *
 * Time comes from the injected {@link Clock}, never from {@code Instant.now()}, so tests can move it.
 */
@Service
public class PoolService {

	private final PoolRepository pools;
	private final PoolMemberRepository members;
	private final PoolItemRepository items;
	private final CommunityRepository communities;
	private final CommunityService communityService;
	private final UserRepository users;
	private final Clock clock;

	public PoolService(PoolRepository pools, PoolMemberRepository members, PoolItemRepository items,
			CommunityRepository communities, CommunityService communityService, UserRepository users,
			Clock clock) {
		this.pools = pools;
		this.members = members;
		this.items = items;
		this.communities = communities;
		this.communityService = communityService;
		this.users = users;
		this.clock = clock;
	}

	// ---- pools -------------------------------------------------------------------------------

	@Transactional
	public PoolDetailResponse create(long userId, long communityId, CreatePoolRequest req) {
		requireCommunityMember(userId, communityId);
		Pool pool = pools.save(new Pool(communityId, userId, req.platform(), req.title().trim(),
				req.targetAmountPaise(), req.closesAt(), req.pickupSpot().trim(), clock.instant()));
		members.save(new PoolMember(pool.getId(), userId));
		return detail(pool, userId);
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
		return detail(pool, userId);
	}

	// ---- membership --------------------------------------------------------------------------

	@Transactional
	public PoolDetailResponse join(long userId, long poolId) {
		Pool pool = lockPool(poolId);
		communityService.requireMembership(userId, pool.getCommunityId());
		pool.requireOpenForChanges(clock.instant());
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
		return detail(pool, userId);
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

	@Transactional
	public PoolDetailResponse deliver(long userId, long poolId) {
		return hostTransition(userId, poolId, PoolTransition.DELIVER);
	}

	// ---- bill split and payments -------------------------------------------------------------

	/**
	 * The {@code order} action: stores every item's final price and the fees, computes every member's
	 * share with {@link BillSplit}, marks the host paid and moves the pool to ORDERED, then to SETTLED
	 * at once if every member is already paid (a pool with only the host). Checks, in order: 404, 403
	 * NOT_A_MEMBER, 403 NOT_HOST, 409 INVALID_TRANSITION unless LOCKED, then 400 VALIDATION_FAILED if
	 * the body does not list every item of the pool exactly once.
	 */
	@Transactional
	public PoolDetailResponse order(long userId, long poolId, OrderRequest req) {
		Pool pool = lockPoolAsHost(userId, poolId);
		pool.requireAllowed(PoolTransition.ORDER);

		List<PoolItem> poolItems = items.findByPoolId(poolId);
		Map<Long, Long> finalPrices = requireEveryItemOnce(poolItems, req.items());
		List<PoolMember> poolMembers = members.findByPoolIdOrderById(poolId);

		Map<Long, Integer> indexByMember = new HashMap<>();
		int hostIndex = -1;
		for (int i = 0; i < poolMembers.size(); i++) {
			indexByMember.put(poolMembers.get(i).getId(), i);
			if (pool.isHost(poolMembers.get(i).getUserId())) {
				hostIndex = i;
			}
		}
		if (hostIndex < 0) {
			throw new IllegalStateException("Host of pool " + poolId + " is not a member");
		}
		long[] itemTotals = new long[poolMembers.size()];
		long[] shares;
		try {
			for (PoolItem item : poolItems) {
				long price = finalPrices.get(item.getId());
				item.setFinalPricePaise(price);
				int i = indexByMember.get(item.getPoolMemberId());
				itemTotals[i] = Math.addExact(itemTotals[i], price);
			}
			shares = BillSplit.shares(itemTotals, req.feesPaise(), hostIndex);
		}
		catch (ArithmeticException ex) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "The order total is too large");
		}

		for (int i = 0; i < poolMembers.size(); i++) {
			poolMembers.get(i).setSharePaise(shares[i]);
		}
		markPaid(poolMembers.get(hostIndex), clock.instant());
		pool.setFeesPaise(req.feesPaise());
		pool.apply(PoolTransition.ORDER);
		settleIfAllPaid(pool);
		pools.flush();
		return detail(pool, userId);
	}

	/**
	 * The host records that a member paid their share. {@code memberId} is the pool_members id.
	 * Checks, in order: 404 POOL_NOT_FOUND, 403 NOT_A_MEMBER, 403 NOT_HOST, 404 MEMBER_NOT_FOUND, 409
	 * INVALID_TRANSITION unless ORDERED or DELIVERED, 409 ALREADY_PAID. Settles the pool in the same
	 * transaction when this was the last unpaid member.
	 */
	@Transactional
	public PoolDetailResponse markMemberPaid(long userId, long poolId, long memberId) {
		Pool pool = lockPoolAsHost(userId, poolId);
		PoolMember member = members.findById(memberId)
				.filter(m -> m.getPoolId().equals(poolId))
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND",
						"No such member in this pool"));
		pool.requireAcceptingPayments();
		if (member.isPaid()) {
			throw new ApiException(HttpStatus.CONFLICT, "ALREADY_PAID", "This member is already marked paid");
		}
		markPaid(member, clock.instant());
		settleIfAllPaid(pool);
		pools.flush();
		return detail(pool, userId);
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
		Pool pool = lockPoolAsHost(userId, poolId);
		pool.apply(transition);
		pools.flush();
		return detail(pool, userId);
	}

	/** Lock the pool, then require community member, then host (403 NOT_HOST), in that order. */
	private Pool lockPoolAsHost(long userId, long poolId) {
		Pool pool = lockPool(poolId);
		communityService.requireMembership(userId, pool.getCommunityId());
		if (!pool.isHost(userId)) {
			throw new ApiException(HttpStatus.FORBIDDEN, "NOT_HOST", "Only the host can do this");
		}
		return pool;
	}

	/**
	 * Lock the pool, then require community member, pool member, and OPEN with {@code closes_at} still
	 * ahead, in that order.
	 */
	private PoolMember lockOpenPoolForMember(long userId, long poolId) {
		Pool pool = lockPool(poolId);
		communityService.requireMembership(userId, pool.getCommunityId());
		PoolMember member = requirePoolMember(poolId, userId);
		pool.requireOpenForChanges(clock.instant());
		return member;
	}

	/**
	 * The {@code order} body must name every item of the pool exactly once. Returns final price by
	 * item id, or throws 400 VALIDATION_FAILED naming every duplicated, foreign and missing item id.
	 */
	private static Map<Long, Long> requireEveryItemOnce(List<PoolItem> poolItems, List<OrderItemRequest> lines) {
		Set<Long> poolItemIds = new HashSet<>();
		for (PoolItem item : poolItems) {
			poolItemIds.add(item.getId());
		}
		Map<Long, Long> finalPrices = new HashMap<>();
		Set<Long> duplicated = new LinkedHashSet<>();
		Set<Long> foreign = new LinkedHashSet<>();
		for (OrderItemRequest line : lines) {
			if (!poolItemIds.contains(line.itemId())) {
				foreign.add(line.itemId());
			}
			else if (finalPrices.putIfAbsent(line.itemId(), line.finalPricePaise()) != null) {
				duplicated.add(line.itemId());
			}
		}
		List<Long> missing = poolItems.stream().map(PoolItem::getId).filter(id -> !finalPrices.containsKey(id))
				.toList();
		List<String> problems = new ArrayList<>();
		if (!duplicated.isEmpty()) {
			problems.add("duplicate item ids " + duplicated);
		}
		if (!foreign.isEmpty()) {
			problems.add("item ids not in this pool " + foreign);
		}
		if (!missing.isEmpty()) {
			problems.add("missing item ids " + missing);
		}
		if (!problems.isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
					"items must list every item of the pool exactly once: " + String.join("; ", problems));
		}
		return finalPrices;
	}

	private static void markPaid(PoolMember member, Instant now) {
		member.setPaid(true);
		member.setPaidAt(now);
	}

	/** ORDERED or DELIVERED to SETTLED once no member is left unpaid. */
	private void settleIfAllPaid(Pool pool) {
		// A JPQL query: Hibernate flushes the pending paid flags before running it.
		if (!members.existsByPoolIdAndPaidFalse(pool.getId())) {
			pool.apply(PoolTransition.SETTLE);
		}
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

	/**
	 * Builds the detail view with a fixed number of queries: members, their users, all items.
	 * {@code hostUpiId} is shown only when {@code viewerId} is a member of the pool, null otherwise.
	 */
	private PoolDetailResponse detail(Pool pool, long viewerId) {
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
		boolean viewerInPool = poolMembers.stream().anyMatch(m -> m.getUserId() == viewerId);

		List<PoolMemberResponse> memberViews = poolMembers.stream()
				.map(m -> new PoolMemberResponse(m.getId(), m.getUserId(), userById.get(m.getUserId()).getName(),
						m.getSharePaise(), m.isPaid(), m.getPaidAt(), itemsByMember.getOrDefault(m.getId(), List.of())))
				.toList();
		return new PoolDetailResponse(pool.getId(), pool.getCommunityId(), pool.getPlatform(), pool.getTitle(),
				pool.getTargetAmountPaise(), current, pool.getClosesAt(), pool.getPickupSpot(), pool.getStatus(),
				pool.getFeesPaise(), host.getId(), host.getName(), viewerInPool ? host.getUpiId() : null, pool.getCreatedAt(), memberViews);
	}

	private static ApiException poolNotFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "POOL_NOT_FOUND", "Pool not found");
	}

	private static ApiException alreadyInPool() {
		return new ApiException(HttpStatus.CONFLICT, "ALREADY_IN_POOL", "You are already in this pool");
	}

}
