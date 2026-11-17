package com.green.chat.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.green.chat.entity.DmMessage;

public interface DmMessageRepository extends JpaRepository<DmMessage, Long> {

	// 이전 대화 (51개: 50개 표시 + 더 있는지 확인용)
	List<DmMessage> findTop51ByConversationIdOrderByIdDesc(Long conversationId);

	List<DmMessage> findTop51ByConversationIdAndIdLessThanOrderByIdDesc(Long conversationId, Long beforeId);

	// 대화방별 마지막 메시지 (목록 미리보기용)
	@Query("""
			select m from DmMessage m
			 where m.id in (select max(m2.id) from DmMessage m2
			                 where m2.conversation.id in :ids
			                 group by m2.conversation.id)
			""")
	List<DmMessage> findLastMessages(@Param("ids") Collection<Long> ids);

	// 대화방별 내가 안 읽은 수: [conversationId, count]
	@Query("""
			select m.conversation.id, count(m.id) from DmMessage m
			 where (m.conversation.user1Id = :userId or m.conversation.user2Id = :userId)
			   and m.senderId <> :userId
			   and m.readAt is null
			 group by m.conversation.id
			""")
	List<Object[]> countUnreadByUser(@Param("userId") Long userId);

	// 전체 안 읽은 수 (앱 아이콘 뱃지용) — 내가 나간(숨긴) 대화는 세지 않음
	@Query("""
			select count(m.id) from DmMessage m
			 where (m.conversation.user1Id = :userId or m.conversation.user2Id = :userId)
			   and m.senderId <> :userId
			   and m.readAt is null
			   and not exists (select c.id from DmConversation c
			                    where c.id = m.conversation.id
			                      and ((c.user1Id = :userId and c.user1Hidden = :hidden)
			                        or (c.user2Id = :userId and c.user2Hidden = :hidden)))
			""")
	long countAllUnread(@Param("userId") Long userId, @Param("hidden") Boolean hidden);

	// 이 대화방에서 내가 받은 메시지를 모두 읽음 처리 (바뀐 행 수 반환)
	@Modifying
	@Query("""
			update DmMessage m set m.readAt = :now
			 where m.conversation.id = :conversationId
			   and m.senderId <> :userId
			   and m.readAt is null
			""")
	int markRead(@Param("conversationId") Long conversationId, @Param("userId") Long userId,
			@Param("now") LocalDateTime now);
}