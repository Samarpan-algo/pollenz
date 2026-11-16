package com.pollenz.community;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MembershipRepository extends JpaRepository<Membership, Long> {

	boolean existsByUserIdAndCommunityId(Long userId, Long communityId);

	Optional<Membership> findByUserIdAndCommunityId(Long userId, Long communityId);

	@Query("""
			select new com.pollenz.community.CommunitySummary(c.id, c.name, c.inviteCode, m.role, c.createdAt)
			from Membership m, Community c
			where m.communityId = c.id and m.userId = :userId
			order by c.id
			""")
	List<CommunitySummary> findSummariesByUserId(@Param("userId") Long userId);

	@Query("""
			select new com.pollenz.community.MemberResponse(u.id, u.name, m.role, m.joinedAt)
			from Membership m, com.pollenz.user.User u
			where m.userId = u.id and m.communityId = :communityId
			order by m.joinedAt, m.id
			""")
	List<MemberResponse> findMembers(@Param("communityId") Long communityId);

}
