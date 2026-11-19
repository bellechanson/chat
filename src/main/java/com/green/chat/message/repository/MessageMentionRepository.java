package com.green.chat.message.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.green.chat.message.entity.MessageMention;

public interface MessageMentionRepository extends JpaRepository<MessageMention, Long> {

	List<MessageMention> findByMessageIdIn(Collection<Long> messageIds);

	// 내가 불렸는데 아직 안 읽은 메시지가 있는 방 (사이드바 "@") — 읽은 곳(last_read_id)보다 뒤에 있으면 안 읽음
	@Query("""
			select distinct m.roomId
			  from MessageMention m, RoomRead r
			 where m.userId = :userId
			   and r.userId = :userId
			   and r.roomId = m.roomId
			   and m.messageId > r.lastReadId
			""")
	List<Long> findUnreadRoomIds(@Param("userId") Long userId);

	@Modifying
	@Query("delete from MessageMention m where m.messageId = :messageId")
	void deleteByMessageId(@Param("messageId") Long messageId);

	@Modifying
	@Query("delete from MessageMention m where m.roomId = :roomId")
	void deleteByRoomId(@Param("roomId") Long roomId);
}