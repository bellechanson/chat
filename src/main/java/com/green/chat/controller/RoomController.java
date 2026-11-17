package com.green.chat.controller;

import java.util.Optional;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.green.chat.entity.ChatRoom;
import com.green.chat.security.LoginUser;
import com.green.chat.service.PresenceService;
import com.green.chat.service.RoomNotifier;
import com.green.chat.service.RoomService;

@Controller
public class RoomController {

	private final RoomService roomService;
	private final PresenceService presenceService;
	private final RoomNotifier notifier;

	public RoomController(RoomService roomService, PresenceService presenceService, RoomNotifier notifier) {
		this.roomService = roomService;
		this.presenceService = presenceService;
		this.notifier = notifier;
	}

	@GetMapping("/")
	public String home() {
		return "redirect:/rooms";
	}

	// 메인 화면 (사이드바 정보는 SidebarAdvice가 따로 넣어 줌)
	@GetMapping("/rooms")
	public String rooms(@AuthenticationPrincipal LoginUser loginUser, Model model) {
		model.addAttribute("rooms", roomService.findAll());
		model.addAttribute("counts", presenceService.counts());
		model.addAttribute("nickname", loginUser.getNickname());
		return "rooms";
	}

	@PostMapping("/rooms")
	public String create(@RequestParam String name, @AuthenticationPrincipal LoginUser loginUser) {
		ChatRoom room = roomService.create(name, loginUser.getId());
		if (room == null)
			return "redirect:/rooms";
		notifier.roomCreated(room);
		return "redirect:/rooms/" + room.getId(); // 만든 방으로 바로 들어감
	}

	@GetMapping("/rooms/{id}")
	public String room(@PathVariable Long id, @AuthenticationPrincipal LoginUser loginUser, Model model) {
		Optional<ChatRoom> found = roomService.findById(id);
		if (found.isEmpty())
			return "redirect:/rooms";
		if (roomService.isBanned(id, loginUser.getId()))
			return "redirect:/rooms?banned";

		ChatRoom room = found.get();
		model.addAttribute("room", room);
		model.addAttribute("nickname", loginUser.getNickname());
		// 방장 또는 관리자 → 강퇴/삭제 버튼 표시
		model.addAttribute("canManage", roomService.canManage(room, loginUser.getId(), loginUser.isAdmin()));
		model.addAttribute("ownerNickname", roomService.ownerNickname(room));
		return "chat";
	}

	@PostMapping("/rooms/{id}/delete")
	public String delete(@PathVariable Long id, @AuthenticationPrincipal LoginUser loginUser) {
		try {
			roomService.delete(id, loginUser.getId(), loginUser.isAdmin());
		} catch (IllegalArgumentException e) {
			return "redirect:/rooms/" + id;
		}
		notifier.roomDeleted(id, loginUser.getNickname(), "채팅방이 삭제되었습니다.");
		return "redirect:/rooms?deleted";
	}
}