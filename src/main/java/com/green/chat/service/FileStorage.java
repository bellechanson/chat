package com.green.chat.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.multipart.MultipartFile;

import com.green.chat.entity.Attachment;

// 업로드 파일을 서버 폴더에 저장하고 내려줌 (DB에는 이름·종류·크기만 기록)
//   폴더 구조: {upload-dir}/rooms/{방번호}/…, {upload-dir}/dm/{대화번호}/…
@Component
public class FileStorage {

	private static final Logger log = LoggerFactory.getLogger(FileStorage.class);

	// 보낼 수 있는 확장자 → 내려줄 때 쓸 종류 (실행 파일, html, svg 등은 목록에 없으니 막힘)
	private static final Map<String, String> TYPES = Map.ofEntries(Map.entry("jpg", "image/jpeg"),
			Map.entry("jpeg", "image/jpeg"), Map.entry("png", "image/png"), Map.entry("gif", "image/gif"),
			Map.entry("webp", "image/webp"), Map.entry("pdf", "application/pdf"), Map.entry("txt", "text/plain"),
			Map.entry("zip", "application/zip"), Map.entry("hwp", "application/x-hwp"),
			Map.entry("doc", "application/msword"),
			Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
			Map.entry("xls", "application/vnd.ms-excel"),
			Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
			Map.entry("ppt", "application/vnd.ms-powerpoint"),
			Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"));

	// 사진 묶음으로 보낼 수 있는 확장자
	private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");

	// 사진 묶음 하나에 넣을 수 있는 최대 장수
	public static final int MAX_PHOTOS = 30;

	private final ImageThumbnails thumbnails;
	private final Path root;
	private final int maxMb; // 파일 하나 최대 크기(MB) — application.yml chat.upload-max-mb
	private final int bundleMaxMb; // 사진 묶음 하나 전체 최대 크기(MB) — application.yml chat.upload-bundle-max-mb

	public FileStorage(@Value("${chat.upload-dir:${user.home}/chat-uploads}") String uploadDir,
			@Value("${chat.upload-max-mb:50}") int maxMb, @Value("${chat.upload-bundle-max-mb:500}") int bundleMaxMb,
			ImageThumbnails thumbnails) throws IOException {
		this.thumbnails = thumbnails;
		this.root = Paths.get(uploadDir).toAbsolutePath().normalize();
		this.maxMb = maxMb;
		this.bundleMaxMb = bundleMaxMb;
		Files.createDirectories(root);
		log.info("[파일 저장 폴더] {} (파일 하나 최대 {}MB, 사진 묶음 최대 {}MB)", root, maxMb, bundleMaxMb);
	}

	// 입력칸(fragments/parts :: composer)의 안내 문구·미리 검사용 (SidebarAdvice가 모델로 넘김)
	public int getMaxMb() {
		return maxMb;
	}

	public int getBundleMaxMb() {
		return bundleMaxMb;
	}

	/**
	 * 사진 묶음 저장: 장수·형식·전체 크기를 먼저 모두 검사 → 하나씩 저장 중간에 저장이 실패하면 앞에서 저장한 사진도 지우고
	 * IllegalArgumentException
	 *
	 * @return 보낸 순서대로 첨부 정보
	 */
	public List<Attachment> storePhotos(String folder, List<MultipartFile> files) {
		if (files == null || files.isEmpty()) {
			throw new IllegalArgumentException("보낼 사진이 없습니다.");
		}
		if (files.size() > MAX_PHOTOS) {
			throw new IllegalArgumentException("사진은 한 번에 " + MAX_PHOTOS + "장까지 보낼 수 있습니다.");
		}
		long total = 0;
		for (MultipartFile f : files) {
			if (f == null || !IMAGE_EXTENSIONS.contains(extension(cleanName(f.getOriginalFilename())))) {
				throw new IllegalArgumentException("사진(jpg, png, gif, webp)만 묶어서 보낼 수 있습니다.");
			}
			total += f.getSize();
		}
		if (total > (long) bundleMaxMb * 1024 * 1024) {
			throw new IllegalArgumentException("사진 묶음은 모두 합쳐 " + bundleMaxMb + "MB까지 보낼 수 있습니다.");
		}

		List<Attachment> saved = new ArrayList<>();
		try {
			for (MultipartFile f : files) {
				saved.add(store(folder, f)); // 빈 파일·한 장 크기 검사도 여기서
			}
		} catch (IllegalArgumentException e) {
			saved.forEach(att -> deleteFile(folder, att.getStoredName())); // 반쯤 저장된 묶음은 남기지 않음
			throw e;
		}
		return saved;
	}

	// 검사 → 저장 → 첨부 정보 반환 (문제가 있으면 IllegalArgumentException, 메시지는 화면에 그대로 표시)
	public Attachment store(String folder, MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException("빈 파일은 보낼 수 없습니다.");
		}
		if (file.getSize() > (long) maxMb * 1024 * 1024) {
			throw new IllegalArgumentException(maxMb + "MB 이하 파일만 보낼 수 있습니다.");
		}

		String name = cleanName(file.getOriginalFilename());
		String ext = extension(name);
		String type = TYPES.get(ext);
		if (type == null) {
			throw new IllegalArgumentException("보낼 수 없는 파일 형식입니다. (사진, PDF, 문서, 한글, ZIP, TXT만 가능)");
		}

		String storedName = UUID.randomUUID() + "." + ext;
		try {
			Path dir = root.resolve(folder);
			Files.createDirectories(dir);
			try (InputStream in = file.getInputStream()) {
				Files.copy(in, dir.resolve(storedName));
			}
		} catch (IOException e) {
			log.error("[파일 저장 실패] {}", name, e);
			throw new IllegalArgumentException("파일을 저장하지 못했습니다. 잠시 후 다시 시도해 주세요.");
		}
		return new Attachment(name, storedName, type, file.getSize());
	}

	// 내려주기: 사진은 브라우저에서 바로 보기, 나머지는 다운로드
	public ResponseEntity<Resource> response(String folder, Attachment att) {
		return response(folder, att, false);
	}

	/**
	 * 내려주기 (thumb=true면 사진은 작은 미리보기로 — 말풍선·사진 묶음 격자용) 미리보기를 만들 필요가 없거나 못 만드는 사진(작은
	 * 사진, gif, webp 등)은 원본
	 */
	public ResponseEntity<Resource> response(String folder, Attachment att, boolean thumb) {
		Path path = resolve(folder, att.getStoredName());
		if (path == null || !Files.isReadable(path)) {
			return ResponseEntity.notFound().build();
		}
		ContentDisposition disposition = ContentDisposition.builder(att.isImage() ? "inline" : "attachment")
				.filename(att.getName(), StandardCharsets.UTF_8).build();
		MediaType type = MediaType.parseMediaType(att.getContentType());
		if (thumb && att.isImage()) {
			Path small = thumbnails.thumbnailOf(path, att.getContentType());
			if (small != null) {
				path = small;
				type = MediaType.IMAGE_JPEG;
			}
		}
		return ResponseEntity.ok().contentType(type).header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
				.cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePrivate())
				.body(new FileSystemResource(path));
	}

	// 파일 하나 삭제 (사진 묶음 저장이 중간에 실패했을 때)
	public void deleteFile(String folder, String storedName) {
		Path path = resolve(folder, storedName);
		if (path == null)
			return;
		try {
			Files.deleteIfExists(path);
		} catch (IOException e) {
			log.warn("[파일 삭제 실패] {}/{}", folder, storedName, e);
		}
	}

	// 방 삭제 등 → 폴더째 삭제
	public void deleteFolder(String folder) {
		Path dir = resolve(folder, "");
		if (dir == null)
			return;
		try {
			FileSystemUtils.deleteRecursively(dir);
		} catch (IOException e) {
			log.warn("[파일 폴더 삭제 실패] {}", folder, e);
		}
	}

	// 저장 폴더 밖으로 나가는 경로(../ 등)는 거부
	private Path resolve(String folder, String storedName) {
		Path path = root.resolve(folder).resolve(storedName).normalize();
		return path.startsWith(root) ? path : null;
	}

	// 경로 부분, 제어 문자 제거 + 200자 제한
	private static String cleanName(String original) {
		String name = original == null ? "" : original;
		name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
		name = name.replaceAll("\\p{Cntrl}", "").trim();
		if (name.isEmpty())
			name = "file";
		return name.length() > 200 ? name.substring(name.length() - 200) : name;
	}

	private static String extension(String name) {
		int dot = name.lastIndexOf('.');
		return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
	}
}