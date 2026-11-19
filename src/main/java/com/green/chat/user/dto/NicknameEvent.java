package com.green.chat.user.dto;

// 누가 닉네임을 바꾸거나 탈퇴함 → 모든 화면의 @멘션 글자, 검색 결과의 보낸 사람을 새 이름으로 (/topic/nickname)
//   nickname : 새 이름 (탈퇴했으면 "(탈퇴한 사용자)"), oldNickname: 바꾸기 전 이름
public record NicknameEvent(Long userId, String nickname, String oldNickname) {
}