package com.green.chat.dto;

// 관리자가 내 계정을 변경했을 때 받는 알림
public record AccountEvent(String message) {
}