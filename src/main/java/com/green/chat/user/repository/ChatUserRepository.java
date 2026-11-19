package com.green.chat.user.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.green.chat.user.entity.ChatUser;
import com.green.chat.user.entity.Role;

public interface ChatUserRepository extends JpaRepository<ChatUser, Long> {

	Optional<ChatUser> findByUsername(String username);

	Optional<ChatUser> findByEmail(String email);

	Optional<ChatUser> findByUsernameAndEmail(String username, String email);

	Optional<ChatUser> findByNickname(String nickname);

	boolean existsByUsername(String username);

	boolean existsByNickname(String nickname);

	boolean existsByEmail(String email);

	// 관리자 수 (마지막 관리자는 탈퇴 못 하게)
	long countByRole(Role role);

	// 관리자: 전체 회원 (최신 가입순, 탈퇴 회원 제외)
	List<ChatUser> findAllByWithdrawnAtIsNullOrderByIdDesc();

	// DM 상대 목록용: 닉네임 가나다순 (탈퇴 회원 제외)
	List<ChatUser> findAllByWithdrawnAtIsNullOrderByNicknameAsc();

	// 관리자: 아이디·닉네임·이메일 검색 (대소문자 무시)
	@Query("""
			select u from ChatUser u
			 where u.withdrawnAt is null
			   and (lower(u.username) like lower(concat('%', :q, '%'))
			    or lower(u.nickname) like lower(concat('%', :q, '%'))
			    or lower(u.email)    like lower(concat('%', :q, '%')))
			 order by u.id desc
			""")
	List<ChatUser> search(@Param("q") String q);
}