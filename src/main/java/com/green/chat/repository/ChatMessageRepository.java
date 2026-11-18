package com.green.chat.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
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

	// 메시지 검색 (최신순, 삭제된 것 제외): 내용에 검색어가 있거나, 이름에 검색어가 들어간 사람을 멘션한 메시지
	// userIds: 검색어가 이름에 들어간 회원 (없으면 -1 하나 — 빈 목록은 IN에 못 씀), 정확한 확인은 서비스에서
	@Query("""
			select m from ChatMessageEntity m
			 where m.room.id = :roomId
			   and m.id < :beforeId
			   and (m.deleted is null or m.deleted = :no)
			   and (lower(m.content) like :pattern escape '\\'
			        or exists (select mm.id from MessageMention mm
			                    where mm.messageId = m.id and mm.userId in :userIds))
			 order by m.id desc
			""")
	List<ChatMessageEntity> search(@Param("roomId") Long roomId, @Param("beforeId") Long beforeId,
			@Param("no") Boolean no, @Param("pattern") String pattern, @Param("userIds") List<Long> userIds,
			Pageable pageable);

	// 사진이 있는 메시지 (모아보기, 최신순, 삭제 제외): 사진 파일 메시지 + 지우지 않은 사진이 남은 묶음
	@Query("""
			select m from ChatMessageEntity m
			 where m.room.id = :roomId
			   and m.id < :beforeId
			   and (m.deleted is null or m.deleted = :no)
			   and (m.attachment.contentType like 'image/%'
			        or exists (select p.id from MessagePhoto p
			                    where p.scope = 'ROOM' and p.messageId = m.id
			                      and (p.deleted is null or p.deleted = :no)))
			 order by m.id desc
			""")
	List<ChatMessageEntity> findWithPhotos(@Param("roomId") Long roomId, @Param("beforeId") Long beforeId,
			@Param("no") Boolean no, Pageable pageable);

	// 사진 파일 메시지 수 (모아보기 전체 개수 = 이것 + 묶음 속 사진 수)
	@Query("""
			select count(m) from ChatMessageEntity m
			 where m.room.id = :roomId
			   and (m.deleted is null or m.deleted = :no)
			   and m.attachment.contentType like 'image/%'
			""")
	long countImageMessages(@Param("roomId") Long roomId, @Param("no") Boolean no);

	@Query("select max(m.id) from ChatMessageEntity m where m.room.id = :roomId")
	Long findMaxIdByRoomId(@Param("roomId") Long roomId);

	@Modifying
	@Query("delete from ChatMessageEntity m where m.room.id = :roomId")
	void deleteByRoomId(@Param("roomId") Long roomId);

	@Modifying
	@Query("update ChatMessageEntity m set m.sender = :newName where m.sender = :oldName")
	int updateSender(@Param("oldName") String oldName, @Param("newName") String newName);
}