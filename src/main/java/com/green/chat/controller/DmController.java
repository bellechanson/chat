package com.green.chat.controller;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.green.chat.entity.DmConversation;
import com.green.chat.security.LoginUser;
import com.green.chat.service.ConnectionTracker;
import com.green.chat.service.DmService;
import com.green.chat.util.Formats;

@Controller
public class DmController {

	// 사용자 고르기 한 줄
	public record UserRow(Long id, String nickname, String initial, int tint, boolean online) {
	}

	private final DmService dmService;
	private final ConnectionTracker connectionTracker;

	public DmController(DmService dmService, ConnectionTracker connectionTracker) {
		this.dmService = dmService;
		this.connectionTracker = connectionTracker;
	}

	// 새 1:1 대화: 대화할 사람 고르기 (대화 목록은 사이드바에 있음)
	@GetMapping("/dm")
	public String list(@AuthenticationPrincipal LoginUser loginUser, Model model) {
		Set<Long> online = connectionTracker.onlineUserIds();

		List<UserRow> users = dmService.selectableUsers(loginUser.getId()).stream()
				.map(u -> new UserRow(u.getId(), u.getNickname(), Formats.initial(u.getNickname()),
						Formats.tint(u.getNickname()), online.contains(u.getId())))
				.sorted(Comparator.comparing((UserRow u) -> !u.online())) // 접속 중인 사람 먼저 (닉네임순 유지)
				.toList();

		model.addAttribute("users", users);
		return "dm-list";
	}

	// 사용자 목록에서 클릭 → 대화 시작
	@PostMapping("/dm/start/{userId}")
	public String startById(@PathVariable Long userId, @AuthenticationPrincipal LoginUser loginUser,
			RedirectAttributes ra) {
		return open(() -> dmService.startById(loginUser.getId(), userId), ra);
	}

	// 닉네임으로 대화 시작 (채팅방 접속자 옆 [1:1] 버튼)
	@PostMapping("/dm/start")
	public String startByNickname(@RequestParam String nickname, @AuthenticationPrincipal LoginUser loginUser,
			RedirectAttributes ra) {
		return open(() -> dmService.startByNickname(loginUser.getId(), nickname), ra);
	}

	// 대화 화면 (참여자만)
	@GetMapping("/dm/{id}")
	public String room(@PathVariable Long id, @AuthenticationPrincipal LoginUser loginUser, Model model) {
		return dmService.findForMember(id, loginUser.getId()).map(c -> {
			Long partnerId = c.partnerOf(loginUser.getId());
			String partnerNickname = dmService.partnerNickname(c, loginUser.getId());

			model.addAttribute("conversationId", c.getId());
			model.addAttribute("myId", loginUser.getId());
			model.addAttribute("partnerId", partnerId);
			model.addAttribute("partnerNickname", partnerNickname);
			model.addAttribute("partnerInitial", Formats.initial(partnerNickname));
			model.addAttribute("partnerTint", Formats.tint(partnerNickname));
			model.addAttribute("partnerOnline", connectionTracker.onlineUserIds().contains(partnerId));
			return "dm-room";
		}).orElse("redirect:/dm");
	}

	// 대화방으로 이동, 실패하면 사용자 고르기 화면에 오류 표시
	private String open(Supplier<DmConversation> starter, RedirectAttributes ra) {
		try {
			return "redirect:/dm/" + starter.get().getId();
		} catch (IllegalArgumentException e) {
			ra.addFlashAttribute("error", e.getMessage());
			return "redirect:/dm";
		}
	}
}