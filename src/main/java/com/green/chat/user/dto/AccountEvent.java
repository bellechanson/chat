package com.green.chat.user.dto;

// 내 계정이 바뀌어 로그아웃시킬 때 받는 알림 (관리자의 권한 변경·정지·강제 로그아웃, 회원 탈퇴)
//   socket.js가 /user/queue/account로 받아서 알림창을 띄우고 로그인 화면으로 보냄
public record AccountEvent(String message) {
}