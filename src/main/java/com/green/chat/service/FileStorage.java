package com.green.chat.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
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
	private static final long MAX_SIZE = 10L * 1024 * 1024; // 10MB

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

	private final Path root;

	public FileStorage(@Value("${chat.upload-dir:${user.home}/chat-uploads}") String uploadDir) throws IOException {
		this.root = Paths.get(uploadDir).toAbsolutePath().normalize();
		Files.createDirectories(root);
		log.info("[파일 저장 폴더] {}", root);
	}

	// 검사 → 저장 → 첨부 정보 반환 (문제가 있으면 IllegalArgumentException, 메시지는 화면에 그대로 표시)
	public Attachment store(String folder, MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException("빈 파일은 보낼 수 없습니다.");
		}
		if (file.getSize() > MAX_SIZE) {
			throw new IllegalArgumentException("10MB 이하 파일만 보낼 수 있습니다.");
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
		Path path = resolve(folder, att.getStoredName());
		if (path == null || !Files.isReadable(path)) {
			return ResponseEntity.notFound().build();
		}
		ContentDisposition disposition = ContentDisposition.builder(att.isImage() ? "inline" : "attachment")
				.filename(att.getName(), StandardCharsets.UTF_8).build();
		return ResponseEntity.ok().contentType(MediaType.parseMediaType(att.getContentType()))
				.header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
				.cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePrivate())
				.body(new FileSystemResource(path));
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