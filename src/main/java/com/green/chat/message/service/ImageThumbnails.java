package com.green.chat.message.service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// 사진 미리보기(썸네일): 긴 쪽 640px JPEG을 원본 옆에 "{저장 이름}.thumb.jpg"로 만들어 둠
//   처음 요청될 때 한 번 만들고 그다음부터는 파일을 그대로 씀 (예전에 올린 사진도 자동으로)
//   만들 필요가 없거나 못 만드는 사진(작은 사진, gif 움짤, webp, 너무 큰 그림, 읽을 수 없는 형식)은 원본을 그대로 씀
//   휴대폰 사진의 회전 정보(EXIF Orientation)를 읽어 바로 세워서 만듦
@Component
public class ImageThumbnails {

	private static final Logger log = LoggerFactory.getLogger(ImageThumbnails.class);

	private static final int SIZE = 640; // 긴 쪽 최대 (격자 칸·말풍선은 300px 안팎 → 고해상도 화면까지 충분)
	private static final long MAX_PIXELS = 60_000_000L; // 이보다 큰 그림은 메모리 보호를 위해 만들지 않음
	private static final float QUALITY = 0.82f;

	// 한 번에 2장까지만 만들기 (대화방을 처음 열 때 수십 장이 한꺼번에 요청돼도 메모리가 튀지 않게)
	private final Semaphore working = new Semaphore(2);

	// 만들지 않기로 한 사진 (매번 다시 검사하지 않게, 서버를 다시 켜면 비워짐)
	private final Set<Path> skipped = ConcurrentHashMap.newKeySet();

	/**
	 * 미리보기 파일 경로 (없으면 지금 만듦)
	 *
	 * @return 미리보기 경로, 만들 필요가 없거나 못 만들면 null → 원본을 쓰면 됨
	 */
	public Path thumbnailOf(Path original, String contentType) {
		if (contentType == null || !contentType.startsWith("image/") || contentType.equals("image/gif"))
			return null; // gif는 움직임이 사라지므로 원본
		Path thumb = original.resolveSibling(original.getFileName() + ".thumb.jpg");
		if (Files.isReadable(thumb))
			return thumb;
		if (skipped.contains(original) || !Files.isReadable(original))
			return null;

		try {
			working.acquire();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return null;
		}
		try {
			if (Files.isReadable(thumb))
				return thumb; // 기다리는 동안 다른 요청이 만들었음
			if (create(original, thumb))
				return thumb;
			skipped.add(original);
			return null;
		} catch (Exception | OutOfMemoryError e) {
			log.warn("[미리보기 만들기 실패] {} ({})", original.getFileName(), e.toString());
			skipped.add(original);
			return null;
		} finally {
			working.release();
		}
	}

	// 원본 읽기 → 줄이기 → 바로 세우기 → JPEG 저장 (만들 필요가 없으면 false)
	private boolean create(Path original, Path thumb) throws IOException {
		BufferedImage src;
		try (ImageInputStream in = ImageIO.createImageInputStream(original.toFile())) {
			if (in == null)
				return false;
			Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
			if (!readers.hasNext())
				return false; // webp 등 자바가 못 읽는 형식
			ImageReader reader = readers.next();
			try {
				reader.setInput(in, true, true);
				int w = reader.getWidth(0), h = reader.getHeight(0);
				if ((long) w * h > MAX_PIXELS)
					return false;
				if (Math.max(w, h) <= SIZE && original.toFile().length() < 300 * 1024)
					return false; // 이미 작은 사진은 원본으로 충분
				src = reader.read(0);
			} finally {
				reader.dispose();
			}
		}

		int orientation = exifOrientation(original);
		double scale = Math.min(1.0, (double) SIZE / Math.max(src.getWidth(), src.getHeight()));
		int tw = Math.max(1, (int) Math.round(src.getWidth() * scale));
		int th = Math.max(1, (int) Math.round(src.getHeight() * scale));
		BufferedImage small = shrink(src, tw, th);

		// 회전 정보 5~8은 가로·세로가 바뀜
		boolean swap = orientation >= 5;
		BufferedImage out = new BufferedImage(swap ? th : tw, swap ? tw : th, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = out.createGraphics();
		try {
			g.setColor(Color.WHITE); // 투명한 PNG는 흰 바탕으로
			g.fillRect(0, 0, out.getWidth(), out.getHeight());
			g.drawImage(small, orientationTransform(orientation, tw, th), null);
		} finally {
			g.dispose();
		}

		writeJpeg(out, thumb);
		return true;
	}

	// 반씩 여러 번 줄이기 (한 번에 크게 줄이면 계단 현상이 생김)
	private static BufferedImage shrink(BufferedImage src, int tw, int th) {
		int type = src.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
		BufferedImage cur = src;
		int w = src.getWidth(), h = src.getHeight();
		do {
			w = Math.max(tw, w / 2);
			h = Math.max(th, h / 2);
			BufferedImage next = new BufferedImage(w, h, type);
			Graphics2D g = next.createGraphics();
			try {
				g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
				g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
				g.drawImage(cur, 0, 0, w, h, null);
			} finally {
				g.dispose();
			}
			cur = next;
		} while (w != tw || h != th);
		return cur;
	}

	// EXIF 회전 값 (1~8) → 줄인 그림(tw × th)을 바로 세우는 변환
	private static AffineTransform orientationTransform(int orientation, int tw, int th) {
		return switch (orientation) {
		case 2 -> new AffineTransform(-1, 0, 0, 1, tw, 0); // 좌우 뒤집기
		case 3 -> new AffineTransform(-1, 0, 0, -1, tw, th); // 180도
		case 4 -> new AffineTransform(1, 0, 0, -1, 0, th); // 위아래 뒤집기
		case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0); // 대각선 뒤집기
		case 6 -> new AffineTransform(0, 1, -1, 0, th, 0); // 시계 방향 90도 (세로로 찍은 휴대폰 사진)
		case 7 -> new AffineTransform(0, -1, -1, 0, th, tw); // 반대 대각선 뒤집기
		case 8 -> new AffineTransform(0, -1, 1, 0, 0, tw); // 반시계 방향 90도
		default -> new AffineTransform();
		};
	}

	// 임시 파일에 쓰고 이름 바꾸기 (만드는 도중의 반쪽 파일을 다른 요청이 읽지 않게)
	private static void writeJpeg(BufferedImage img, Path target) throws IOException {
		ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
		Path tmp = Files.createTempFile(target.getParent(), "thumb", ".tmp");
		try {
			try (ImageOutputStream out = ImageIO.createImageOutputStream(tmp.toFile())) {
				ImageWriteParam param = writer.getDefaultWriteParam();
				param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
				param.setCompressionQuality(QUALITY);
				writer.setOutput(out);
				writer.write(null, new IIOImage(img, null, null), param);
			}
			Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
		} finally {
			writer.dispose();
			Files.deleteIfExists(tmp);
		}
	}

	// ── JPEG 안의 EXIF 회전 값 읽기 (없거나 JPEG가 아니면 1 = 그대로) ──
	static int exifOrientation(Path path) {
		byte[] b;
		try (InputStream in = Files.newInputStream(path)) {
			b = in.readNBytes(256 * 1024); // EXIF는 파일 앞부분에 있음
		} catch (IOException e) {
			return 1;
		}
		if (b.length < 4 || (b[0] & 0xFF) != 0xFF || (b[1] & 0xFF) != 0xD8)
			return 1; // JPEG가 아님
		int i = 2;
		while (i + 4 <= b.length && (b[i] & 0xFF) == 0xFF) {
			int marker = b[i + 1] & 0xFF;
			int len = u16(b, i + 2, false);
			if (marker == 0xDA || marker == 0xD9 || len < 2)
				return 1; // 그림 데이터 시작 → EXIF 없음
			int start = i + 4, end = Math.min(b.length, i + 2 + len);
			if (marker == 0xE1 && end - start > 14 && b[start] == 'E' && b[start + 1] == 'x' && b[start + 2] == 'i'
					&& b[start + 3] == 'f' && b[start + 4] == 0 && b[start + 5] == 0) {
				return tiffOrientation(b, start + 6, end);
			}
			i += 2 + len;
		}
		return 1;
	}

	private static int tiffOrientation(byte[] b, int tiff, int end) {
		if (tiff + 8 > end)
			return 1;
		boolean little = b[tiff] == 'I' && b[tiff + 1] == 'I';
		long ifd = tiff + u32(b, tiff + 4, little);
		if (ifd < tiff || ifd + 2 > end)
			return 1;
		int count = u16(b, (int) ifd, little);
		for (int k = 0; k < count; k++) {
			int entry = (int) ifd + 2 + k * 12;
			if (entry + 12 > end)
				break;
			if (u16(b, entry, little) == 0x0112) { // Orientation 태그
				int v = u16(b, entry + 8, little);
				return (v >= 1 && v <= 8) ? v : 1;
			}
		}
		return 1;
	}

	private static int u16(byte[] b, int i, boolean little) {
		int a = b[i] & 0xFF, c = b[i + 1] & 0xFF;
		return little ? (c << 8) | a : (a << 8) | c;
	}

	private static long u32(byte[] b, int i, boolean little) {
		long v = 0;
		for (int k = 0; k < 4; k++) {
			int byteVal = b[little ? i + 3 - k : i + k] & 0xFF;
			v = (v << 8) | byteVal;
		}
		return v;
	}
}