package com.green.chat.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.green.chat.entity.ChatUser;

public interface ChatUserRepository extends JpaRepository<ChatUser, Long> {

	Optional<ChatUser> findByUsername(String username);

	Optional<ChatUser> findByEmail(String email);

	Optional<ChatUser> findByUsernameAndEmail(String username, String email);

	Optional<ChatUser> findByNickname(String nickname);

	boolean existsByUsername(String username);

	boolean existsByNickname(String nickname);

	boolean existsByEmail(String email);

	// 관리자: 전체 회원 (최신 가입순)
	List<ChatUser> findAllByOrderByIdDesc();

	// DM 상대 목록용: 닉네임 가나다순
	List<ChatUser> findAllByOrderByNicknameAsc();

	// 관리자: 아이디·닉네임·이메일 검색 (대소문자 무시)
	@Query("""
			select u from ChatUser u
			 where lower(u.username) like lower(concat('%', :q, '%'))
			    or lower(u.nickname) like lower(concat('%', :q, '%'))
			    or lower(u.email)    like lower(concat('%', :q, '%'))
			 order by u.id desc
			""")
	List<ChatUser> search(@Param("q") String q);
}