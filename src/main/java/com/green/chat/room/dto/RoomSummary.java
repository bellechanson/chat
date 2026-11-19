package com.green.chat.room.dto;

import com.green.chat.room.entity.ChatRoom;

// 새 방이 생기면 열려 있는 화면에 실시간으로 보낼 방 정보
public record RoomSummary(Long id, String name) {

	public static RoomSummary from(ChatRoom room) {
		return new RoomSummary(room.getId(), room.getName());
	}
}