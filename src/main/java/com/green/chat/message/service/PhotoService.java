package com.green.chat.message.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.green.chat.message.dto.FileDto;
import com.green.chat.message.entity.Attachment;
import com.green.chat.message.entity.MessagePhoto;
import com.green.chat.message.repository.MessagePhotoRepository;

// 사진 묶음 메시지의 사진들 (채팅방·DM 공통)
//   메시지가 그 방/대화 것인지, 지울 권한이 있는지는 부르는 쪽(ChatService, DmService)에서 먼저 확인
@Service
@Transactional(readOnly = true)
public class PhotoService {

	private final MessagePhotoRepository photoRepository;

	public PhotoService(MessagePhotoRepository photoRepository) {
		this.photoRepository = photoRepository;
	}

	// 저장해 둔 사진 파일들을 메시지에 순서대로 연결
	@Transactional
	public List<MessagePhoto> save(String scope, Long messageId, List<Attachment> attachments) {
		return photoRepository.saveAll(IntStream.range(0, attachments.size())
				.mapToObj(i -> new MessagePhoto(scope, messageId, i, attachments.get(i))).toList());
	}

	// 여러 메시지의 사진을 한 번에 (메시지 번호 → 사진들, 이전 대화 불러오기용)
	public Map<Long, List<MessagePhoto>> photosOf(String scope, Collection<Long> messageIds) {
		if (messageIds.isEmpty())
			return Map.of();
		Map<Long, List<MessagePhoto>> map = new LinkedHashMap<>();
		for (MessagePhoto p : photoRepository.findByScopeAndMessageIdInOrderByMessageIdAscSortOrderAsc(scope,
				messageIds)) {
			if (!p.isDeleted())
				map.computeIfAbsent(p.getMessageId(), k -> new ArrayList<>()).add(p);
		}
		return map;
	}

	// 묶음 속 사진 수 (모아보기 전체 개수용, 지운 사진·메시지 제외)
	public long countRoomPhotos(Long roomId) {
		return photoRepository.countRoomPhotos(roomId, Boolean.FALSE);
	}

	public long countDmPhotos(Long conversationId) {
		return photoRepository.countDmPhotos(conversationId, Boolean.FALSE);
	}

	// 메시지 하나의 사진들 → 모아보기용 목록 (묶음이면 남은 사진 전부, 사진 파일이면 한 장, 아니면 빈 목록)
	public List<FileDto> photosOfMessage(String fileUrl, Attachment attachment, List<MessagePhoto> album) {
		if (album != null && !album.isEmpty())
			return FileDto.photos(fileUrl, album);
		if (attachment != null && attachment.isImage())
			return List.of(FileDto.of(attachment, fileUrl));
		return List.of();
	}

	// 사진 묶음 메시지인지 (수정 막기용)
	public boolean isAlbum(String scope, Long messageId) {
		return photoRepository.existsByScopeAndMessageId(scope, messageId);
	}

	// 이 메시지의 (지우지 않은) 사진 한 장 — 내려받기용
	public Optional<Attachment> attachment(String scope, Long messageId, Long photoId) {
		return find(scope, messageId, photoId).map(MessagePhoto::getAttachment);
	}

	/**
	 * 묶음에서 한 장 지우기 (삭제 표시만)
	 *
	 * @return 남은 사진들 (비었으면 부르는 쪽에서 메시지 전체를 삭제 처리)
	 */
	@Transactional
	public List<MessagePhoto> deleteOne(String scope, Long messageId, Long photoId) {
		MessagePhoto photo = find(scope, messageId, photoId)
				.orElseThrow(() -> new IllegalArgumentException("이미 지워졌거나 없는 사진입니다."));
		photo.delete();
		photoRepository.flush(); // 바로 아래에서 다시 읽으므로 먼저 반영
		return photoRepository.findByScopeAndMessageIdOrderBySortOrderAsc(scope, messageId).stream()
				.filter(p -> !p.isDeleted()).toList();
	}

	// 다른 메시지의 사진 번호를 넣어도 찾지 못하게 (scope + 메시지 번호까지 확인)
	private Optional<MessagePhoto> find(String scope, Long messageId, Long photoId) {
		if (photoId == null)
			return Optional.empty();
		return photoRepository.findById(photoId)
				.filter(p -> p.getScope().equals(scope) && p.getMessageId().equals(messageId) && !p.isDeleted());
	}
}