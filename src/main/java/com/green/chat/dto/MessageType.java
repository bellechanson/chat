package com.green.chat.dto;

public enum MessageType {
    ENTER,            // 입장
    TALK,             // 일반 메시지
    LEAVE,            // 퇴장
    KICK,             // 강퇴 (sender = 강퇴된 사람)
    ROOM_DELETED,     // 방 삭제
    MESSAGE_DELETED   // 메시지 삭제 (id = 삭제된 메시지 번호)
}