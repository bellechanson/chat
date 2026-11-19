package com.green.chat.message.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.green.chat.message.dto.ReactionDto;
import com.green.chat.message.entity.MessageReaction;
import com.green.chat.message.repository.MessageReactionRepository;
import com.green.chat.user.entity.ChatUser;
import com.green.chat.user.repository.ChatUserRepository;

// 이모지 반응 (채팅방·DM 공통). 메시지가 그 방/대화 것인지, 누를 수 있는지는 부르는 쪽에서 먼저 확인
@Service
@Transactional(readOnly = true)
public class ReactionService {

	// 고를 수 있는 이모지 (화면 순서 그대로, messages.js의 REACTIONS와 같은 이름)
	public static final List<String> EMOJIS = List.of("like", "heart", "laugh", "wow", "sad", "pray");
	private static final Set<String> ALLOWED = Set.copyOf(EMOJIS);

	private final MessageReactionRepository reactionRepository;
	private final ChatUserRepository userRepository;

	public ReactionService(MessageReactionRepository reactionRepository, ChatUserRepository userRepository) {
		this.reactionRepository = reactionRepository;
		this.userRepository = userRepository;
	}

	/**
	 * 반응 누르기: 처음이면 추가, 같은 걸 다시 누르면 취소, 다른 걸 누르면 바꿈
	 *
	 * @return 이 메시지의 반응 전체 (모두의 화면에 보낼 것)
	 */
	@Transactional
	public List<ReactionDto> toggle(String scope, Long messageId, Long userId, String emoji) {
		if (emoji == null || !ALLOWED.contains(emoji)) {
			throw new IllegalArgumentException("고를 수 없는 반응입니다.");
		}
		reactionRepository.findByScopeAndMessageIdAndUserId(scope, messageId, userId).ifPresentOrElse(r -> {
			if (r.getEmoji().equals(emoji))
				reactionRepository.delete(r); // 같은 걸 다시 → 취소
			else
				r.change(emoji); // 다른 걸 → 바꿈 (변경 감지로 UPDATE)
		}, () -> reactionRepository.save(new MessageReaction(scope, messageId, userId, emoji)));
		reactionRepository.flush(); // 바로 아래에서 다시 읽으므로 먼저 반영

		return summaries(scope, List.of(messageId)).getOrDefault(messageId, List.of());
	}

	/**
	 * 여러 메시지의 반응을 한 번에 (이전 대화 불러오기용)
	 *
	 * @return 메시지 번호 → 반응 칩 목록 (반응이 없는 메시지는 빠짐)
	 */
	public Map<Long, List<ReactionDto>> summaries(String scope, Collection<Long> messageIds) {
		if (messageIds.isEmpty())
			return Map.of();
		List<MessageReaction> rows = reactionRepository.findByScopeAndMessageIdInOrderByCreatedAtAscIdAsc(scope,
				messageIds);
		if (rows.isEmpty())
			return Map.of();

		// 누른 사람 이름 한 번에 (탈퇴했으면 "(탈퇴한 사용자)")
		Map<Long, String> names = userRepository
				.findAllById(rows.stream().map(MessageReaction::getUserId).distinct().toList()).stream()
				.collect(Collectors.toMap(ChatUser::getId, ChatUser::getDisplayName));

		// 메시지별 → 이모지별로 모으기
		Map<Long, Map<String, List<MessageReaction>>> grouped = new HashMap<>();
		for (MessageReaction r : rows) {
			grouped.computeIfAbsent(r.getMessageId(), k -> new LinkedHashMap<>())
					.computeIfAbsent(r.getEmoji(), k -> new ArrayList<>()).add(r);
		}

		Map<Long, List<ReactionDto>> result = new HashMap<>();
		grouped.forEach((messageId, byEmoji) -> {
			List<ReactionDto> chips = new ArrayList<>();
			for (String emoji : EMOJIS) { // 칩 순서는 항상 👍 ❤️ 😂 😮 😢 🙏
				List<MessageReaction> list = byEmoji.get(emoji);
				if (list == null)
					continue;
				chips.add(new ReactionDto(emoji, list.stream().map(MessageReaction::getUserId).toList(),
						list.stream().map(r -> names.getOrDefault(r.getUserId(), ChatUser.WITHDRAWN_NAME)).toList()));
			}
			result.put(messageId, chips);
		});
		return result;
	}
}