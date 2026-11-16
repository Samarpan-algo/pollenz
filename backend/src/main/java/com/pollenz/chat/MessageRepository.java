package com.pollenz.chat;

import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, Long> {

	/** Newest first, sender name joined in one query. Callers reverse it to oldest first. */
	@Query("""
			select new com.pollenz.chat.ChatMessageResponse(m.id, m.poolId, m.senderId, u.name, m.body, m.createdAt)
			from Message m, User u
			where u.id = m.senderId and m.poolId = :poolId
			order by m.id desc""")
	List<ChatMessageResponse> findLatest(@Param("poolId") long poolId, Limit limit);

}
