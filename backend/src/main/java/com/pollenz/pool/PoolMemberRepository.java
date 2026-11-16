package com.pollenz.pool;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PoolMemberRepository extends JpaRepository<PoolMember, Long> {

	Optional<PoolMember> findByPoolIdAndUserId(Long poolId, Long userId);

	boolean existsByPoolIdAndUserId(Long poolId, Long userId);

	long countByPoolId(Long poolId);

	/** True while at least one member of the pool is not marked paid. */
	boolean existsByPoolIdAndPaidFalse(Long poolId);

	/** Members in join order; the host, added at creation, comes first. */
	List<PoolMember> findByPoolIdOrderById(Long poolId);

}
