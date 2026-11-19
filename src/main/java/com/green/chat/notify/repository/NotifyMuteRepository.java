package com.green.chat.notify.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.green.chat.notify.entity.NotifyMute;

public interface NotifyMuteRepository extends JpaRepository<NotifyMute, Long> {

	Optional<NotifyMute> findByScopeAndTargetIdAndUserId(String scope, Long targetId, Long userId);

	List<NotifyMute> findByUserId(Long userId);

	// 이 방 알림을 지금 꺼 둔 회원의 로그인 아이디 (알림 보낼 때 빼려고)
	@Query("""
			select u.username
			  from NotifyMute n, ChatUser u
			 where n.scope = :scope
			   and n.targetId = :targetId
			   and u.id = n.userId
			   and (n.mutedUntil is null or n.mutedUntil > :now)
			""")
	List<String> findMutedUsernames(@Param("scope") String scope, @Param("targetId") Long targetId,
			@Param("now") LocalDateTime now);
}