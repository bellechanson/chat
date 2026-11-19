package com.green.chat.room.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.green.chat.room.entity.RoomBan;

public interface RoomBanRepository extends JpaRepository<RoomBan, Long> {

	boolean existsByRoomIdAndUserId(Long roomId, Long userId);

	// 관리자 화면: 전체 강퇴 목록 (최신순)
	List<RoomBan> findAllByOrderByIdDesc();

	// 관리자 화면: 방별 강퇴 수 [roomId, count]
	@Query("select b.roomId, count(b.id) from RoomBan b group by b.roomId")
	List<Object[]> countGroupByRoom();

	@Modifying
	@Query("delete from RoomBan b where b.roomId = :roomId")
	void deleteByRoomId(@Param("roomId") Long roomId);
}