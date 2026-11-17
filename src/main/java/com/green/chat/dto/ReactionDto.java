package com.green.chat.dto;

import java.util.List;

// 메시지 아래 반응 칩 하나: 3
//   emoji   : "like", "heart" … (화면에서 이모지 글자로 바꿈 → messages.js REACTIONS)
//   userIds : 누른 사람 id (내가 눌렀는지 화면에서 내 id와 비교)
//   names   : 누른 사람 이름 (칩에 마우스를 올리면 "철수, 영희님이 반응")
public record ReactionDto(String emoji, List<Long> userIds, List<String> names) {
}