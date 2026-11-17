package com.green.chat.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.green.chat.entity.MessagePhoto;

public interface MessagePhotoRepository extends JpaRepository<MessagePhoto, Long> {

	// 여러 메시지의 사진을 한 번에 (묶음 안 순서대로, 지운 사진은 PhotoService에서 걸러 냄)
	List<MessagePhoto> findByScopeAndMessageIdInOrderByMessageIdAscSortOrderAsc(String scope,
			Collection<Long> messageIds);

	// 이 메시지의 사진들 (순서대로)
	List<MessagePhoto> findByScopeAndMessageIdOrderBySortOrderAsc(String scope, Long messageId);

	// 사진 묶음 메시지인지 (수정 막기용 — 다 지워진 묶음도 묶음)
	boolean existsByScopeAndMessageId(String scope, Long messageId);

	// 채팅방을 지울 때 그 방 메시지들의 사진 정보도 함께 삭제 (파일은 폴더째 따로 지움)
	@Modifying
	@Query("""
			delete from MessagePhoto p
			 where p.scope = 'ROOM'
			   and p.messageId in (select m.id from ChatMessageEntity m where m.room.id = :roomId)
			""")
	void deleteRoomPhotos(@Param("roomId") Long roomId);
}