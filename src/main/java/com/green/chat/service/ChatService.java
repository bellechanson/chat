package com.green.chat.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.green.chat.dto.ChatMessage;
import com.green.chat.dto.MessagePage;
import com.green.chat.dto.ReactionDto;
import com.green.chat.dto.ReactionEvent;
import com.green.chat.entity.Attachment;
import com.green.chat.entity.ChatMessageEntity;
import com.green.chat.entity.ChatRoom;
import com.green.chat.entity.MessageReaction;
import com.green.chat.repository.ChatMessageRepository;
import com.green.chat.repository.ChatRoomRepository;

@Service
@Transactional(readOnly = true)
public class ChatService {

	private static final int PAGE_SIZE = 50;

	private final ChatRoomRepository roomRepository;
	private final ChatMessageRepository messageRepository;
	private final FileStorage fileStorage;
	private final ReactionService reactionService;

	public ChatService(ChatRoomRepository roomRepository, ChatMessageRepository messageRepository,
			FileStorage fileStorage, ReactionService reactionService) {
		this.roomRepository = roomRepository;
		this.messageRepository = messageRepository;
		this.fileStorage = fileStorage;
		this.reactionService = reactionService;
	}

	@Transactional
	public ChatMessage save(Long roomId, String sender, String content) {
		ChatRoom room = roomRepository.getReferenceById(roomId);
		ChatMessageEntity saved = messageRepository.save(new ChatMessageEntity(room, sender, content));
		return ChatMessage.from(saved);
	}

	// 파일 메시지: 파일 저장 → 메시지 저장 (내용에는 "사진" / "파일: 이름")
	@Transactional
	public ChatMessage saveFile(Long roomId, String sender, MultipartFile file) {
		Attachment att = fileStorage.store("rooms/" + roomId, file);
		ChatRoom room = roomRepository.getReferenceById(roomId);
		ChatMessageEntity saved = messageRepository.save(new ChatMessageEntity(room, sender, att.label(), att));
		return ChatMessage.from(saved);
	}

	/**
	 * 메시지 한 묶음 조회
	 *
	 * @param beforeId null이면 최신 50개, 값이 있으면 그 번호보다 오래된 50개
	 */
	public MessagePage messages(Long roomId, Long beforeId) {
		List<ChatMessageEntity> rows = (beforeId == null) ? messageRepository.findTop51ByRoomIdOrderByIdDesc(roomId)
				: messageRepository.findTop51ByRoomIdAndIdLessThanOrderByIdDesc(roomId, beforeId);

		boolean hasMore = rows.size() > PAGE_SIZE;
		List<ChatMessageEntity> page = new ArrayList<>(hasMore ? rows.subList(0, PAGE_SIZE) : rows);
		Collections.reverse(page); // 최신순 → 오래된순 (화면 표시 순서)

		// 이 묶음의 반응을 한 번에 가져와서 메시지마다 붙임
		Map<Long, List<ReactionDto>> reactions = reactionService.summaries(MessageReaction.ROOM,
				page.stream().map(ChatMessageEntity::getId).toList());
		return new MessagePage(
				page.stream().map(e -> ChatMessage.from(e, reactions.getOrDefault(e.getId(), List.of()))).toList(),
				hasMore);
	}

	// 내려받을 파일 (이 방의, 삭제 안 된 파일 메시지만)
	public Optional<Attachment> attachment(Long roomId, Long messageId) {
		return findInRoom(roomId, messageId).filter(m -> !m.isDeleted()).map(ChatMessageEntity::getAttachment);
	}

	/**
	 * 메시지 삭제 (삭제 표시만, 내용은 DB에 남김)
	 *
	 * @param canManage 방장·관리자면 true → 다른 사람 메시지도 삭제 가능
	 */
	@Transactional
	public void delete(Long roomId, Long messageId, String nickname, boolean canManage) {
		ChatMessageEntity m = messageIn(roomId, messageId);
		if (!canManage && !m.getSender().equals(nickname)) {
			throw new IllegalArgumentException("내 메시지만 삭제할 수 있습니다.");
		}
		m.delete(); // 변경 감지로 UPDATE
	}

	/**
	 * 메시지 수정: 내 글 메시지만 (방장·관리자도 남의 메시지는 못 고침, 사진·파일 메시지는 수정 불가)
	 *
	 * @return 방 사람들에게 보낼 "수정됨" 신호
	 */
	@Transactional
	public ChatMessage edit(Long roomId, Long messageId, String nickname, String content) {
		ChatMessageEntity m = messageIn(roomId, messageId);
		if (!m.getSender().equals(nickname)) {
			throw new IllegalArgumentException("내 메시지만 수정할 수 있습니다.");
		}
		if (m.isDeleted()) {
			throw new IllegalArgumentException("삭제된 메시지는 수정할 수 없습니다.");
		}
		if (m.getAttachment() != null) {
			throw new IllegalArgumentException("사진·파일 메시지는 수정할 수 없습니다.");
		}
		if (content.isEmpty()) {
			throw new IllegalArgumentException("빈 메시지로는 수정할 수 없습니다.");
		}
		if (content.equals(m.getContent())) {
			throw new IllegalArgumentException("바뀐 내용이 없습니다.");
		}
		m.edit(content); // 변경 감지로 UPDATE
		return ChatMessage.edited(m);
	}

	/**
	 * 이모지 반응 누르기 (이 방의, 삭제 안 된 메시지만 — 사진·파일 메시지에도 가능)
	 *
	 * @return 방 사람들에게 보낼 "반응 바뀜" 신호
	 */
	@Transactional
	public ReactionEvent react(Long roomId, Long messageId, Long userId, String emoji) {
		ChatMessageEntity m = messageIn(roomId, messageId);
		if (m.isDeleted()) {
			throw new IllegalArgumentException("삭제된 메시지에는 반응할 수 없습니다.");
		}
		List<ReactionDto> reactions = reactionService.toggle(MessageReaction.ROOM, messageId, userId, emoji);
		return new ReactionEvent(null, messageId, reactions);
	}

	// ── 여러 기능이 같이 쓰는 도우미 ──

	// 이 방의 메시지 (다른 방 번호로 남의 메시지를 건드리지 못하게)
	private Optional<ChatMessageEntity> findInRoom(Long roomId, Long messageId) {
		return messageRepository.findById(messageId).filter(m -> m.getRoom().getId().equals(roomId));
	}

	// 이 방의 메시지, 없으면 예외 (삭제·수정·반응)
	private ChatMessageEntity messageIn(Long roomId, Long messageId) {
		return findInRoom(roomId, messageId).orElseThrow(() -> new IllegalArgumentException("존재하지 않는 메시지입니다."));
	}
}