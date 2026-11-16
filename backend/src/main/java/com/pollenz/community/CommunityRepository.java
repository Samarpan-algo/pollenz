package com.pollenz.community;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CommunityRepository extends JpaRepository<Community, Long> {

	Optional<Community> findByInviteCode(String inviteCode);

	boolean existsByInviteCode(String inviteCode);

}
