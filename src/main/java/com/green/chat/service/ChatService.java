package com.green.chat.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.green.chat.dto.ChatMessage;
import com.green.chat.dto.MessagePage;
import com.green.chat.dto.ReactionDto;
import com.green.chat.dto.ReactionEvent;
import com.green.chat.dto.ReplyDto;
import com.green.chat.entity.Attachment;
import com.green.chat.entity.ChatMessageEntity;
import com.green.chat.entity.ChatRoom;
import com.green.chat.entity.MessagePhoto;
import com.green.chat.entity.MessageReaction;
import com.green.chat.repository.ChatMessageRepository;
import com.green.chat.repository.ChatRoomRepository;

@Service
@Transactional(readOnly = true)
public class ChatService {

	private static final int PAGE_SIZE = 50;

	// 글 메시지 보내기 결과: 화면용 메시지 + 부른 사람 (알림 끈 방에서도 알림)
	// mentioned: @멘션한 사람, repliedTo: 답장한 원래 메시지를 보낸 사람 (멘션도 했으면 mentioned에만)
	public record Sent(ChatMessage message, Set<Long> mentioned, Set<Long> repliedTo) {
	}

	private final ChatRoomRepository roomRepository;
	private final ChatMessageRepository messageRepository;
	private final FileStorage fileStorage;
	private final ReactionService reactionService;
	private final PhotoService photoService;
	private final MentionService mentionService;

	public ChatService(ChatRoomRepository roomRepository, ChatMessageRepository messageRepository,
			FileStorage fileStorage, ReactionService reactionService, PhotoService photoService,
			MentionService mentionService) {
		this.roomRepository = roomRepository;
		this.messageRepository = messageRepository;
		this.fileStorage = fileStorage;
		this.reactionService = reactionService;
		this.photoService = photoService;
		this.mentionService = mentionService;
	}

	/**
	 * 글 메시지 보내기: "@닉네임"은 멘션으로 바꿔 저장, 답장이면 원래 메시지 번호도
	 *
	 * @param replyToId 답장할 메시지 번호 (이 방의 삭제 안 된 메시지만, 아니면 그냥 보통 메시지로)
	 */
	@Transactional
	public Sent save(Long roomId, Long senderId, String sender, String content, Long replyToId) {
		ChatRoom room = roomRepository.getReferenceById(roomId);
		ChatMessageEntity original = replyToId == null ? null
				: findInRoom(roomId, replyToId).filter(m -> !m.isDeleted()).orElse(null);
		MentionService.Parsed parsed = mentionService.parse(roomId, content, senderId);

		ChatMessageEntity message = new ChatMessageEntity(room, sender, parsed.content());
		if (original != null)
			message.replyTo(original.getId());
		ChatMessageEntity saved = messageRepository.save(message);

		Set<Long> mentioned = parsed.userIds();
		Set<Long> repliedTo = mentionService.replyTarget(original, senderId).filter(id -> !mentioned.contains(id))
				.map(Set::of).orElse(Set.of());
		Set<Long> called = new LinkedHashSet<>(mentioned);
		called.addAll(repliedTo);
		mentionService.save(roomId, saved.getId(), called);

		ReplyDto reply = original == null ? null : replyOf(original, mentionService.mentionsOf(List.of(original)));
		ChatMessage dto = ChatMessage.from(saved, List.of(), List.of(), reply, mentionService.names(mentioned));
		return new Sent(dto, mentioned, repliedTo);
	}

	// 파일 메시지: 파일 저장 → 메시지 저장 (내용에는 "사진" / "파일: 이름")
	@Transactional
	public ChatMessage saveFile(Long roomId, String sender, MultipartFile file) {
		Attachment att = fileStorage.store("rooms/" + roomId, file);
		ChatRoom room = roomRepository.getReferenceById(roomId);
		ChatMessageEntity saved = messageRepository.save(new ChatMessageEntity(room, sender, att.label(), att));
		return ChatMessage.from(saved);
	}

	// 사진 묶음 메시지: 사진들 저장 → 메시지 하나(내용 "사진") + 사진 정보 (최대 30장)
	@Transactional
	public ChatMessage savePhotos(Long roomId, String sender, List<MultipartFile> files) {
		List<Attachment> atts = fileStorage.storePhotos("rooms/" + roomId, files);
		ChatRoom room = roomRepository.getReferenceById(roomId);
		ChatMessageEntity saved = messageRepository.save(new ChatMessageEntity(room, sender, "사진"));
		List<MessagePhoto> photos = photoService.save(MessageReaction.ROOM, saved.getId(), atts);
		return ChatMessage.from(saved, List.of(), photos);
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

		// 이 묶음의 반응·사진 묶음·답장 원래 메시지·멘션 이름을 한 번에 가져와서 메시지마다 붙임
		List<Long> ids = page.stream().map(ChatMessageEntity::getId).toList();
		Map<Long, List<ReactionDto>> reactions = reactionService.summaries(MessageReaction.ROOM, ids);
		Map<Long, List<MessagePhoto>> photos = photoService.photosOf(MessageReaction.ROOM, ids);
		Map<Long, ChatMessageEntity> originals = originalsOf(page);
		List<ChatMessageEntity> all = new ArrayList<>(page);
		all.addAll(originals.values());
		Map<Long, Map<Long, String>> mentions = mentionService.mentionsOf(all);

		return new MessagePage(page.stream().map(e -> {
			ChatMessageEntity original = e.getReplyToId() == null ? null : originals.get(e.getReplyToId());
			ReplyDto reply = e.getReplyToId() == null ? null
					: original == null ? new ReplyDto(e.getReplyToId(), null, null, true) : replyOf(original, mentions);
			return ChatMessage.from(e, reactions.getOrDefault(e.getId(), List.of()),
					photos.getOrDefault(e.getId(), List.of()), reply, mentions.getOrDefault(e.getId(), Map.of()));
		}).toList(), hasMore);
	}

	// 내려받을 파일 (이 방의, 삭제 안 된 파일 메시지만)
	public Optional<Attachment> attachment(Long roomId, Long messageId) {
		return findInRoom(roomId, messageId).filter(m -> !m.isDeleted()).map(ChatMessageEntity::getAttachment);
	}

	// 사진 묶음 속 한 장 내려받기 (이 방의, 삭제 안 된 메시지의, 지우지 않은 사진만)
	public Optional<Attachment> photo(Long roomId, Long messageId, Long photoId) {
		if (findInRoom(roomId, messageId).filter(m -> !m.isDeleted()).isEmpty())
			return Optional.empty();
		return photoService.attachment(MessageReaction.ROOM, messageId, photoId);
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
		mentionService.deleteFor(messageId); // 지운 메시지로는 "@"가 남지 않게
	}

	/**
	 * 메시지 수정: 내 글 메시지만 (방장·관리자도 남의 메시지는 못 고침, 사진·파일 메시지는 수정 불가)
	 *
	 * @return 방 사람들에게 보낼 "수정됨" 신호
	 */
	@Transactional
	public ChatMessage edit(Long roomId, Long messageId, Long userId, String nickname, String content) {
		ChatMessageEntity m = messageIn(roomId, messageId);
		if (!m.getSender().equals(nickname)) {
			throw new IllegalArgumentException("내 메시지만 수정할 수 있습니다.");
		}
		if (m.isDeleted()) {
			throw new IllegalArgumentException("삭제된 메시지는 수정할 수 없습니다.");
		}
		if (m.getAttachment() != null || photoService.isAlbum(MessageReaction.ROOM, messageId)) {
			throw new IllegalArgumentException("사진·파일 메시지는 수정할 수 없습니다.");
		}
		if (content.isEmpty()) {
			throw new IllegalArgumentException("빈 메시지로는 수정할 수 없습니다.");
		}
		MentionService.Parsed parsed = mentionService.parse(roomId, content, userId); // "@닉네임" → 멘션
		if (parsed.content().equals(m.getContent())) {
			throw new IllegalArgumentException("바뀐 내용이 없습니다.");
		}
		m.edit(parsed.content()); // 변경 감지로 UPDATE

		// 부른 사람 다시 저장 (답장 상대는 그대로, 새로 부른 사람에게 알림은 다시 보내지 않음)
		ChatMessageEntity original = m.getReplyToId() == null ? null
				: findInRoom(roomId, m.getReplyToId()).orElse(null);
		Set<Long> called = new LinkedHashSet<>(parsed.userIds());
		mentionService.replyTarget(original, userId).ifPresent(called::add);
		mentionService.replace(roomId, messageId, called);
		return ChatMessage.edited(m, mentionService.names(parsed.userIds()));
	}

	/**
	 * 사진 묶음에서 한 장만 지우기 (지울 수 있는 사람은 메시지 삭제와 같음)
	 *
	 * @param canManage 방장·관리자면 true → 다른 사람 사진도 삭제 가능
	 * @return 방 사람들에게 보낼 신호: 남은 사진이 있으면 "사진 바뀜", 마지막 한 장이었으면 "메시지 삭제됨"
	 */
	@Transactional
	public ChatMessage deletePhoto(Long roomId, Long messageId, Long photoId, String nickname, boolean canManage) {
		ChatMessageEntity m = messageIn(roomId, messageId);
		if (!canManage && !m.getSender().equals(nickname)) {
			throw new IllegalArgumentException("내 사진만 삭제할 수 있습니다.");
		}
		if (m.isDeleted()) {
			throw new IllegalArgumentException("이미 삭제된 메시지입니다.");
		}
		List<MessagePhoto> remaining = photoService.deleteOne(MessageReaction.ROOM, messageId, photoId);
		if (remaining.isEmpty()) {
			m.delete(); // 다 지웠으면 메시지도 삭제 (변경 감지로 UPDATE)
			mentionService.deleteFor(messageId);
			return ChatMessage.deleted(messageId);
		}
		return ChatMessage.photosChanged(m, remaining);
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

	// 답장들이 가리키는 원래 메시지 (이 묶음 밖의 예전 메시지도) : 번호 → 메시지
	private Map<Long, ChatMessageEntity> originalsOf(List<ChatMessageEntity> page) {
		List<Long> replyIds = page.stream().map(ChatMessageEntity::getReplyToId).filter(Objects::nonNull).distinct()
				.toList();
		if (replyIds.isEmpty())
			return new HashMap<>();
		return messageRepository.findAllById(replyIds).stream()
				.collect(Collectors.toMap(ChatMessageEntity::getId, Function.identity()));
	}

	// 원래 메시지 → 인용 (멘션은 "@이름"으로)
	private static ReplyDto replyOf(ChatMessageEntity original, Map<Long, Map<Long, String>> mentions) {
		String text = MentionService.plain(original.getContent(), mentions.get(original.getId()));
		return ReplyDto.of(original.getId(), original.getSender(), text, original.isDeleted());
	}

	// 이 방의 메시지 (다른 방 번호로 남의 메시지를 건드리지 못하게)
	private Optional<ChatMessageEntity> findInRoom(Long roomId, Long messageId) {
		return messageRepository.findById(messageId).filter(m -> m.getRoom().getId().equals(roomId));
	}

	// 이 방의 메시지, 없으면 예외 (삭제·수정·반응)
	private ChatMessageEntity messageIn(Long roomId, Long messageId) {
		return findInRoom(roomId, messageId).orElseThrow(() -> new IllegalArgumentException("존재하지 않는 메시지입니다."));
	}
}