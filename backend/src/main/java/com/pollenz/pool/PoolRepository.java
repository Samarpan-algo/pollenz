package com.pollenz.pool;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface PoolRepository extends JpaRepository<Pool, Long> {

	/**
	 * Loads the pool and locks its row ({@code SELECT ... FOR NO KEY UPDATE} on PostgreSQL) until the
	 * transaction ends. Call it inside a transaction, before the pool is loaded any other way in that
	 * transaction: an already managed instance would come back with its stale state.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Pool p where p.id = :id")
	Optional<Pool> findByIdForUpdate(@Param("id") Long id);

	/** Pool cards of one community, newest first, with the item total and member count computed in SQL. */
	@Query("""
			select new com.pollenz.pool.PoolSummary(p.id, p.communityId, p.platform, p.title, p.targetAmountPaise,
				(select coalesce(sum(i.estPricePaise), 0L) from PoolItem i, PoolMember im
					where i.poolMemberId = im.id and im.poolId = p.id),
				(select count(m) from PoolMember m where m.poolId = p.id),
				p.closesAt, p.pickupSpot, p.status, p.hostId, u.name, p.createdAt)
			from Pool p, com.pollenz.user.User u
			where u.id = p.hostId and p.communityId = :communityId
				and (:status is null or p.status = :status)
			order by p.createdAt desc, p.id desc
			""")
	List<PoolSummary> findSummaries(@Param("communityId") Long communityId, @Param("status") PoolStatus status);

	/** Ids of OPEN pools whose deadline has passed, oldest deadline first. */
	@Query("select p.id from Pool p where p.status = com.pollenz.pool.PoolStatus.OPEN and p.closesAt <= :now"
			+ " order by p.closesAt, p.id")
	List<Long> findIdsDueForExpiry(@Param("now") Instant now);

}
