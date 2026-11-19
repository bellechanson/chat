package com.green.chat.message.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.green.chat.message.entity.MessagePhoto;

public interface MessagePhotoRepository extends JpaRepository<MessagePhoto, Long> {

	// 여러 메시지의 사진을 한 번에 (묶음 안 순서대로, 지운 사진은 PhotoService에서 걸러 냄)
	List<MessagePhoto> findByScopeAndMessageIdInOrderByMessageIdAscSortOrderAsc(String scope,
			Collection<Long> messageIds);

	// 이 메시지의 사진들 (순서대로)
	List<MessagePhoto> findByScopeAndMessageIdOrderBySortOrderAsc(String scope, Long messageId);

	// 사진 묶음 메시지인지 (수정 막기용 — 다 지워진 묶음도 묶음)
	boolean existsByScopeAndMessageId(String scope, Long messageId);

	// 이 방 묶음 속 사진 수 (지운 사진, 지운 메시지 제외 — 모아보기 전체 개수)
	@Query("""
			select count(p) from MessagePhoto p, ChatMessageEntity m
			 where p.scope = 'ROOM'
			   and p.messageId = m.id
			   and m.room.id = :roomId
			   and (p.deleted is null or p.deleted = :no)
			   and (m.deleted is null or m.deleted = :no)
			""")
	long countRoomPhotos(@Param("roomId") Long roomId, @Param("no") Boolean no);

	// 이 1:1 대화 묶음 속 사진 수 (지운 사진, 지운 메시지 제외)
	@Query("""
			select count(p) from MessagePhoto p, DmMessage m
			 where p.scope = 'DM'
			   and p.messageId = m.id
			   and m.conversation.id = :conversationId
			   and (p.deleted is null or p.deleted = :no)
			   and (m.deleted is null or m.deleted = :no)
			""")
	long countDmPhotos(@Param("conversationId") Long conversationId, @Param("no") Boolean no);

	// 채팅방을 지울 때 그 방 메시지들의 사진 정보도 함께 삭제 (파일은 폴더째 따로 지움)
	@Modifying
	@Query("""
			delete from MessagePhoto p
			 where p.scope = 'ROOM'
			   and p.messageId in (select m.id from ChatMessageEntity m where m.room.id = :roomId)
			""")
	void deleteRoomPhotos(@Param("roomId") Long roomId);
}