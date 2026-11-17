package com.green.chat.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.green.chat.entity.ChatMessageEntity;

public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, Long> {

	// 최신 51개 (50개 보여주고, 51번째가 있으면 "더 있음")
	List<ChatMessageEntity> findTop51ByRoomIdOrderByIdDesc(Long roomId);

	// beforeId보다 오래된 것 중 최신 51개
	List<ChatMessageEntity> findTop51ByRoomIdAndIdLessThanOrderByIdDesc(Long roomId, Long beforeId);

	@Query("select max(m.id) from ChatMessageEntity m where m.room.id = :roomId")
	Long findMaxIdByRoomId(@Param("roomId") Long roomId);

	@Modifying
	@Query("delete from ChatMessageEntity m where m.room.id = :roomId")
	void deleteByRoomId(@Param("roomId") Long roomId);

	@Modifying
	@Query("update ChatMessageEntity m set m.sender = :newName where m.sender = :oldName")
	int updateSender(@Param("oldName") String oldName, @Param("newName") String newName);
}