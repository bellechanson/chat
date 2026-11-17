package com.green.chat.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.green.chat.dto.ChatMessage;
import com.green.chat.dto.MessagePage;
import com.green.chat.entity.Attachment;
import com.green.chat.entity.ChatMessageEntity;
import com.green.chat.entity.ChatRoom;
import com.green.chat.repository.ChatMessageRepository;
import com.green.chat.repository.ChatRoomRepository;

@Service
@Transactional(readOnly = true)
public class ChatService {

	private static final int PAGE_SIZE = 50;

	private final ChatRoomRepository roomRepository;
	private final ChatMessageRepository messageRepository;
	private final FileStorage fileStorage;

	public ChatService(ChatRoomRepository roomRepository, ChatMessageRepository messageRepository,
			FileStorage fileStorage) {
		this.roomRepository = roomRepository;
		this.messageRepository = messageRepository;
		this.fileStorage = fileStorage;
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

		return new MessagePage(page.stream().map(ChatMessage::from).toList(), hasMore);
	}

	// 내려받을 파일 (이 방의, 삭제 안 된 파일 메시지만)
	public Optional<Attachment> attachment(Long roomId, Long messageId) {
		return messageRepository.findById(messageId).filter(m -> m.getRoom().getId().equals(roomId))
				.filter(m -> !m.isDeleted()).map(ChatMessageEntity::getAttachment);
	}

	/**
	 * 메시지 삭제 (삭제 표시만, 내용은 DB에 남김)
	 *
	 * @param canManage 방장·관리자면 true → 다른 사람 메시지도 삭제 가능
	 */
	@Transactional
	public void delete(Long roomId, Long messageId, String nickname, boolean canManage) {
		ChatMessageEntity m = messageRepository.findById(messageId).filter(msg -> msg.getRoom().getId().equals(roomId))
				.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 메시지입니다."));
		if (!canManage && !m.getSender().equals(nickname)) {
			throw new IllegalArgumentException("내 메시지만 삭제할 수 있습니다.");
		}
		m.delete(); // 변경 감지로 UPDATE
	}
}