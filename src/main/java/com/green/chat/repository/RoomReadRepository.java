package com.green.chat.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.green.chat.entity.ChatUser;
import com.green.chat.entity.RoomRead;

public interface RoomReadRepository extends JpaRepository<RoomRead, Long> {

	Optional<RoomRead> findByRoomIdAndUserId(Long roomId, Long userId);

	List<RoomRead> findByUserId(Long userId);

	// 사용자의 방별 안 읽은 메시지 수를 한 번에 계산
	// 결과: [roomId, count] 목록 (안 읽은 게 0인 방은 결과에 없음)
	@Query("""
			select r.roomId, count(m.id)
			  from RoomRead r, ChatMessageEntity m
			 where r.userId = :userId
			   and m.room.id = r.roomId
			   and m.id > r.lastReadId
			 group by r.roomId
			""")
	List<Object[]> countUnreadByUser(@Param("userId") Long userId);

	// 이 방에 들어와 본 적 있는 회원의 로그인 아이디 (한 사람 제외)
	@Query("""
			select u.username
			  from RoomRead r, ChatUser u
			 where r.roomId = :roomId
			   and u.id = r.userId
			   and u.id <> :excludeUserId
			""")
	List<String> findMemberUsernames(@Param("roomId") Long roomId, @Param("excludeUserId") Long excludeUserId);

	// 이 방에 들어와 본 적 있는 회원 (@멘션 자동완성·찾기)
	@Query("""
			select u
			  from RoomRead r, ChatUser u
			 where r.roomId = :roomId
			   and u.id = r.userId
			""")
	List<ChatUser> findMembers(@Param("roomId") Long roomId);

	@Modifying
	@Query("delete from RoomRead r where r.roomId = :roomId")
	void deleteByRoomId(@Param("roomId") Long roomId);

	@Modifying
	@Query("delete from RoomRead r where r.roomId = :roomId and r.userId = :userId")
	void deleteByRoomIdAndUserId(@Param("roomId") Long roomId, @Param("userId") Long userId);
}