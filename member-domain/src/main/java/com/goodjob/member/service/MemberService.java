package com.goodjob.member.service;

import com.goodjob.common.nickname.service.NicknameGenerator;
import com.goodjob.member.coin.CoinUt;
import com.goodjob.member.constant.ProviderType;
import com.goodjob.member.dto.request.JoinRequestDto;
import com.goodjob.member.entity.Member;
import com.goodjob.member.jwt.JwtProvider;
import com.goodjob.member.repository.MemberRepository;
import com.goodjob.common.rsData.RsData;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.goodjob.member.constant.Membership.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {
    private final PasswordEncoder passwordEncoder;
    private final MemberRepository memberRepository;
    private final JwtProvider jwtProvider;

    private final NicknameGenerator nicknameGenerator;

    @Transactional
    public RsData<Member> join(JoinRequestDto joinRequestDto) {
        RsData canJoinData = canJoin(joinRequestDto);

        if (canJoinData.isFail()) { // 회원가입 불가능할 경우
            return canJoinData;
        }

        String password = passwordEncoder.encode(joinRequestDto.getPassword());
        String nickname = joinRequestDto.getNickname().replaceAll("\\s+", "");

        Member member = genMember(joinRequestDto.getUsername(), password, nickname, joinRequestDto.getEmail(), ProviderType.GOODJOB);

        memberRepository.save(member);

        return RsData.of("S-1", "%s님의 회원가입이 완료되었습니다.".formatted(joinRequestDto.getNickname()), member);
    }

    private RsData canJoin(JoinRequestDto joinRequestDto) {
        Optional<Member> opUsername = findByUsername(joinRequestDto.getUsername());
        Optional<Member> opNickname = findByNickname(joinRequestDto.getNickname());

        if (opUsername.isPresent()) { // 로그인 계정이 중복인 경우
            return RsData.of("F-1", "이미 존재하는 계정입니다.");
        }

        if (opNickname.isPresent()) { // 닉네임이 중복인 경우
            return RsData.of("F-1", "이미 존재하는 닉네임입니다.");
        }

        return RsData.of("S-1", "회원가입이 가능합니다.");
    }

    // 일반회원가입, 소셜로그인 회원가입 나눠 처리
    @Transactional
    public RsData<Member> socialJoin(String providerType, String username, String password, String email) {
        if (StringUtils.hasText(password)) {
            password = passwordEncoder.encode(password);
        }

        // 닉네임을 랜덤으로 생성
        String nickname = nicknameGenerator.getRandomNickname();

        // oauth2 로그인 이후 추가정보입력
        Member member = genMember(username, password, nickname, email, ProviderType.of(providerType));

        memberRepository.save(member);

        return RsData.of("S-1", "%s님의 회원가입이 완료되었습니다.".formatted(nickname), member);
    }

    private Member genMember(String username, String password, String nickname, String email, ProviderType providerType) {
        return Member
                .builder()
                .username(username)
                .password(password)
                .nickname(nickname)
                .email(email)
                .isDeleted(false)
                .providerType(providerType)
                .membership(FREE)
                .coin(CoinUt.MAX_COIN_COUNT)
                .build();
    }

    public RsData<Map<String, String>> login(String username, String password) {
        Optional<Member> opMember = findByUsername(username);

        if (opMember.isEmpty()) {
            return RsData.of("F-1", "아이디 혹은 비밀번호가 틀립니다.");
        }

        Member member = opMember.get();

        if (member.isDeleted()) {
            return RsData.of("F-1", "탈퇴 처리중인 회원입니다.");
        }

        boolean matches = passwordEncoder.matches(password, member.getPassword());

        if (!matches) {
            return RsData.of("F-1", "아이디 혹은 비밀번호가 틀립니다.");
        }

        Map<String, String> tokens = jwtProvider.genAccessTokenAndRefreshToken(member);

        return RsData.of("S-1", "%s님 환영합니다!".formatted(member.getNickname()), tokens);
    }

    // 소셜 로그인할때마다 동작
    public RsData<Member> whenSocialLogin(String providerType, String username, String email) {
        Optional<Member> opMember = findByUsername(username);

        if (opMember.isPresent()) {
            return RsData.of("S-1", "로그인 되었습니다.", opMember.get());
        }

        return socialJoin(providerType, username, "", email); // 최초 1회 실행
    }

    @Transactional
    public void upgradeToPremiumMembership(Member member) {
        member.upgradeMembership(PREMIUM);

        memberRepository.save(member);
    }

    @Transactional
    public RsData upgradeToMentorMembership(Member member) {
        member.upgradeMembership(MENTOR);

        memberRepository.save(member);

        return RsData.of("S-1", "멘토 등급이 되었습니다.");
    }

    @Transactional
    public void deductCoin(Member member) {
        member.deductCoin();

        memberRepository.save(member);
    }

    @Transactional
    public void updateCoins() {
        memberRepository.updateCoinForFreeMembers(CoinUt.MAX_COIN_COUNT);
    }

    @Transactional
    public void recoverCoins(long memberId) {
        Member member = findById(memberId).orElse(null);
        member.recoverCoin();

        memberRepository.save(member);
    }

    public Optional<Member> findByNickname(String nickname) {
        return memberRepository.findByNickname(nickname);
    }

    public Optional<Member> findByUsername(String username) {
        return memberRepository.findByUsername(username);
    }

    public Optional<Member> findById(Long id) {
        return memberRepository.findById(id);
    }

    public Optional<Member> findByEmail(String email) {
        return memberRepository.findByEmail(email);
    }

    @Transactional
    public void softDelete(Long id) {
        Member member = findById(id).orElse(null);
        member.softDelete();

        memberRepository.save(member);
    }
}