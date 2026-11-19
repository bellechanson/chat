package com.green.chat.sidebar.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.green.chat.admin.controller.AdminController;
import com.green.chat.dm.controller.DmController;
import com.green.chat.global.security.LoginUser;
import com.green.chat.message.service.FileStorage;
import com.green.chat.room.controller.RoomController;
import com.green.chat.sidebar.service.SidebarService;
import com.green.chat.user.controller.MyPageController;

import jakarta.servlet.http.HttpServletRequest;

// 사이드바가 있는 화면들: 화면을 그릴 때마다 사이드바 내용(+ 입력칸이 쓰는 값)을 모델에 넣어 줌
@ControllerAdvice(assignableTypes = { RoomController.class, DmController.class, MyPageController.class,
		AdminController.class })
public class SidebarAdvice {

	private final SidebarService sidebarService;
	private final FileStorage fileStorage;

	public SidebarAdvice(SidebarService sidebarService, FileStorage fileStorage) {
		this.sidebarService = sidebarService;
		this.fileStorage = fileStorage;
	}

	@ModelAttribute("sidebar")
	public SidebarService.View sidebar(@AuthenticationPrincipal LoginUser loginUser, HttpServletRequest request) {
		// 저장·삭제 같은 POST 요청은 화면을 그리지 않으니 건너뜀
		if (loginUser == null || !"GET".equalsIgnoreCase(request.getMethod()))
			return null;
		return sidebarService.view(loginUser);
	}

	// 입력칸(fragments/parts :: composer)의 파일 크기 안내·미리 검사용 (application.yml
	// chat.upload-max-mb)
	// 템플릿에서 ${@fileStorage...}처럼 빈을 직접 부르면 Thymeleaf 3.1 보안 제한에 걸려서 모델로 넘김
	@ModelAttribute("uploadMaxMb")
	public int uploadMaxMb() {
		return fileStorage.getMaxMb();
	}

	// 사진 묶음 하나 전체 최대 크기(MB) (application.yml chat.upload-bundle-max-mb) — 넘으면 화면에서
	// 묶음을 나눠 보냄
	@ModelAttribute("uploadBundleMaxMb")
	public int uploadBundleMaxMb() {
		return fileStorage.getBundleMaxMb();
	}
}