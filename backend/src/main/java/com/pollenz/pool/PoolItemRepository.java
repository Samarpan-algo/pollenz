package com.pollenz.pool;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PoolItemRepository extends JpaRepository<PoolItem, Long> {

	/** All items of a pool in one query, oldest first. */
	@Query("""
			select i from PoolItem i
			where i.poolMemberId in (select m.id from PoolMember m where m.poolId = :poolId)
			order by i.id
			""")
	List<PoolItem> findByPoolId(@Param("poolId") Long poolId);

	/** The item, only if it belongs to a member of this pool. */
	@Query("""
			select i from PoolItem i, PoolMember m
			where i.id = :itemId and i.poolMemberId = m.id and m.poolId = :poolId
			""")
	Optional<PoolItem> findInPool(@Param("poolId") Long poolId, @Param("itemId") Long itemId);

	// No clearAutomatically: that would detach the locked Pool. Callers must not hold these items loaded.
	@Modifying(flushAutomatically = true)
	@Query("delete from PoolItem i where i.poolMemberId = :poolMemberId")
	int deleteByPoolMemberId(@Param("poolMemberId") Long poolMemberId);

}
