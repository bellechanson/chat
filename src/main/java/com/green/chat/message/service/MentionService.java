package com.green.chat.message.service;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.green.chat.message.entity.MessageMention;
import com.green.chat.message.repository.MessageMentionRepository;
import com.green.chat.room.entity.ChatMessageEntity;
import com.green.chat.room.repository.RoomReadRepository;
import com.green.chat.user.entity.ChatUser;
import com.green.chat.user.repository.ChatUserRepository;

// 채팅방 @멘션 · 답장으로 부르기
//   저장: 보낼 때 "@닉네임"을 "<@회원번호>"로 바꿔 저장 → 닉네임이 바뀌어도 화면은 새 이름으로
//   화면: 메시지마다 mentions {회원번호: 지금 이름}을 같이 보내고, 화면이 "<@번호>"를 "@이름"으로 그림
//   불린 사람은 message_mention에 저장 (알림 끈 방에서도 알림, 사이드바 "@")
@Service
@Transactional(readOnly = true)
public class MentionService {

	public record Member(Long id, String nickname) {
	}

	// content: "<@번호>"로 바꾼 내용, userIds: 부른 사람 (나 빼고, 쓴 순서)
	public record Parsed(String content, Set<Long> userIds) {
	}

	private static final Pattern TOKEN = Pattern.compile("<@(\\d+)>");
	private static final int MAX_CONTENT = 1000;

	private final MessageMentionRepository mentionRepository;
	private final RoomReadRepository readRepository;
	private final ChatUserRepository userRepository;

	public MentionService(MessageMentionRepository mentionRepository, RoomReadRepository readRepository,
			ChatUserRepository userRepository) {
		this.mentionRepository = mentionRepository;
		this.readRepository = readRepository;
		this.userRepository = userRepository;
	}

	// 이 방에 들어와 본 회원 (@ 자동완성, 멘션 찾기) — 탈퇴한 사람 제외, 이름 순
	public List<Member> members(Long roomId) {
		return readRepository.findMembers(roomId).stream().filter(u -> !u.isWithdrawn())
				.map(u -> new Member(u.getId(), u.getNickname())).sorted(Comparator.comparing(Member::nickname))
				.toList();
	}

	// "@영희 안녕" → "<@12> 안녕" (이 방 회원 이름만, 긴 이름부터 맞춰 봄 → "@영희님"도 영희)
	// 나 자신은 바꾸지 않음, 바꾸면 1000자가 넘으면 그냥 글자 그대로
	public Parsed parse(Long roomId, String text, Long excludeUserId) {
		if (text.indexOf('@') < 0)
			return new Parsed(text, Set.of());

		List<Member> candidates = members(roomId).stream().filter(m -> !m.id().equals(excludeUserId))
				.sorted(Comparator.comparingInt((Member m) -> m.nickname().length()).reversed()).toList();
		StringBuilder out = new StringBuilder();
		Set<Long> ids = new LinkedHashSet<>();
		int i = 0;
		while (i < text.length()) {
			char c = text.charAt(i);
			Member hit = null;
			if (c == '@') {
				for (Member m : candidates) {
					if (text.startsWith(m.nickname(), i + 1)) {
						hit = m;
						break;
					}
				}
			}
			if (hit != null) {
				out.append("<@").append(hit.id()).append('>');
				ids.add(hit.id());
				i += 1 + hit.nickname().length();
			} else {
				out.append(c);
				i++;
			}
		}
		if (ids.isEmpty() || out.length() > MAX_CONTENT)
			return new Parsed(text, Set.of());
		return new Parsed(out.toString(), ids);
	}

	// 답장으로 부를 사람: 원래 메시지를 보낸 회원 (나 자신, 탈퇴한 사람이면 없음)
	public Optional<Long> replyTarget(ChatMessageEntity original, Long excludeUserId) {
		if (original == null)
			return Optional.empty();
		return userRepository.findByNickname(original.getSender()).filter(u -> !u.isWithdrawn()).map(ChatUser::getId)
				.filter(id -> !id.equals(excludeUserId));
	}

	// 불린 사람 저장 (보낼 때)
	@Transactional
	public void save(Long roomId, Long messageId, Collection<Long> userIds) {
		for (Long userId : new LinkedHashSet<>(userIds)) {
			mentionRepository.save(new MessageMention(roomId, messageId, userId));
		}
	}

	// 불린 사람 다시 저장 (수정할 때 — 새로 부른 사람에게 알림은 다시 보내지 않음)
	@Transactional
	public void replace(Long roomId, Long messageId, Collection<Long> userIds) {
		mentionRepository.deleteByMessageId(messageId);
		save(roomId, messageId, userIds);
	}

	@Transactional
	public void deleteFor(Long messageId) {
		mentionRepository.deleteByMessageId(messageId);
	}

	@Transactional
	public void deleteRoom(Long roomId) {
		mentionRepository.deleteByRoomId(roomId);
	}

	// 내가 불렸는데 아직 안 읽은 방 번호 (사이드바 "@")
	public Set<Long> unreadRooms(Long userId) {
		return new HashSet<>(mentionRepository.findUnreadRoomIds(userId));
	}

	// 메시지마다 화면에 그릴 멘션 이름: 메시지 번호 → {회원번호: 지금 이름}
	// 내용 속 "<@번호>" 중 저장된 멘션만 (직접 "<@5>"라고 적은 글자는 그대로 보임)
	public Map<Long, Map<Long, String>> mentionsOf(Collection<ChatMessageEntity> messages) {
		List<ChatMessageEntity> withToken = messages.stream()
				.filter(m -> !m.isDeleted() && m.getContent() != null && m.getContent().contains("<@")).toList();
		if (withToken.isEmpty())
			return Map.of();

		Map<Long, Set<Long>> saved = new HashMap<>();
		for (MessageMention mm : mentionRepository
				.findByMessageIdIn(withToken.stream().map(ChatMessageEntity::getId).toList())) {
			saved.computeIfAbsent(mm.getMessageId(), k -> new HashSet<>()).add(mm.getUserId());
		}
		Map<Long, Map<Long, String>> result = new HashMap<>();
		for (ChatMessageEntity m : withToken) {
			Set<Long> ids = tokenIds(m.getContent());
			ids.retainAll(saved.getOrDefault(m.getId(), Set.of()));
			if (!ids.isEmpty())
				result.put(m.getId(), names(ids));
		}
		return result;
	}

	// 회원번호 → 지금 이름 (탈퇴했으면 "(탈퇴한 사용자)")
	public Map<Long, String> names(Collection<Long> userIds) {
		if (userIds.isEmpty())
			return Map.of();
		Map<Long, String> found = userRepository.findAllById(userIds).stream()
				.collect(Collectors.toMap(ChatUser::getId, ChatUser::getDisplayName));
		Map<Long, String> result = new LinkedHashMap<>();
		for (Long id : userIds) {
			result.put(id, found.getOrDefault(id, ChatUser.WITHDRAWN_NAME));
		}
		return result;
	}

	// 회원번호 → 로그인 아이디 (데스크톱 알림 보낼 곳)
	public Map<Long, String> usernames(Collection<Long> userIds) {
		if (userIds.isEmpty())
			return Map.of();
		return userRepository.findAllById(userIds).stream().filter(u -> !u.isWithdrawn())
				.collect(Collectors.toMap(ChatUser::getId, ChatUser::getUsername));
	}

	// "<@12> 안녕" → "@영희 안녕" (알림·답장 미리보기용 글자)
	public static String plain(String content, Map<Long, String> names) {
		if (content == null || names == null || names.isEmpty())
			return content;
		Matcher m = TOKEN.matcher(content);
		StringBuilder sb = new StringBuilder();
		while (m.find()) {
			String name = names.get(Long.valueOf(m.group(1)));
			m.appendReplacement(sb, Matcher.quoteReplacement(name == null ? m.group() : "@" + name));
		}
		m.appendTail(sb);
		return sb.toString();
	}

	private static Set<Long> tokenIds(String content) {
		Set<Long> ids = new LinkedHashSet<>();
		Matcher m = TOKEN.matcher(content);
		while (m.find()) {
			try {
				ids.add(Long.valueOf(m.group(1)));
			} catch (NumberFormatException e) {
				// 너무 긴 숫자는 무시
			}
		}
		return ids;
	}
}