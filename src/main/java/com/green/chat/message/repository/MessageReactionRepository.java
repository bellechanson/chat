package com.green.chat.message.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.green.chat.message.entity.MessageReaction;

public interface MessageReactionRepository extends JpaRepository<MessageReaction, Long> {

	// 이 메시지에 내가 단 반응 (한 사람당 하나)
	Optional<MessageReaction> findByScopeAndMessageIdAndUserId(String scope, Long messageId, Long userId);

	// 여러 메시지의 반응을 한 번에 (먼저 누른 순서대로)
	List<MessageReaction> findByScopeAndMessageIdInOrderByCreatedAtAscIdAsc(String scope, Collection<Long> messageIds);

	// 채팅방을 지울 때 그 방 메시지들의 반응도 함께 삭제
	@Modifying
	@Query("""
			delete from MessageReaction r
			 where r.scope = 'ROOM'
			   and r.messageId in (select m.id from ChatMessageEntity m where m.room.id = :roomId)
			""")
	void deleteRoomReactions(@Param("roomId") Long roomId);
}