package com.green.chat.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.green.chat.entity.DmConversation;

public interface DmConversationRepository extends JpaRepository<DmConversation, Long> {

	Optional<DmConversation> findByUser1IdAndUser2Id(Long user1Id, Long user2Id);

	// 내 대화 목록 (최근 대화순)
	@Query("""
			select c from DmConversation c
			 where c.user1Id = :userId or c.user2Id = :userId
			 order by c.lastMessageAt desc
			""")
	List<DmConversation> findMine(@Param("userId") Long userId);
}