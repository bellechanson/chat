package com.green.chat.room.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.green.chat.room.entity.ChatRoom;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    // 메서드 이름만으로 쿼리 자동 생성: 최신순 정렬
    List<ChatRoom> findAllByOrderByIdDesc();
}