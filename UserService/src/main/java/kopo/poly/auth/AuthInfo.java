package kopo.poly.auth;

import kopo.poly.dto.UserInfoDTO;
import kopo.poly.util.CmmUtil;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@Getter
@RequiredArgsConstructor
public class AuthInfo implements UserDetails {

    // 로그인된 사용자 정보
    // UserInfoRepository로부터 조회된 정보를 저장하기 위한 객체
    private final UserInfoDTO userInfoDTO;

    /**
     * 로그인한 사용자의 권한 부여하기
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {

        Set<GrantedAuthority> pSet = new HashSet<>();

        String roles = CmmUtil.nvl(userInfoDTO.getRoles());

        log.info("getAuthorities / roles : " + roles);
        if (roles.length() > 0) { //DB에 저장된 Role이 있는 경우에만 실행
            for (String role : roles.split(",")) {
                pSet.add(new SimpleGrantedAuthority(role));

            }
        }

        return pSet;
    }

    /**
     * 사용자의 id를 반환 (unique한 값)
     */
    @Override
    public String getUsername() {
        return CmmUtil.nvl(userInfoDTO.getUserId());

    }

    // 사용자의 password를 반환
    @Override
    public String getPassword() {
        return CmmUtil.nvl(userInfoDTO.getPassword());
    }

    // 계정 만료 여부 반환
    @Override
    public boolean isAccountNonExpired() {
        return true; // true -> 만료되지 않았음
    }

    // 계정 잠금 여부 반환
    @Override
    public boolean isAccountNonLocked() {
        return true; // true -> 잠금되지 않았음
    }

    // 패스워드의 만료 여부 반환
    @Override
    public boolean isCredentialsNonExpired() {
        return true; // true -> 만료되지 않았음
    }

    // 계정 사용 가능 여부 반환
    @Override
    public boolean isEnabled() {
        return true; // true -> 사용 가능
    }

}
