package kopo.poly.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import kopo.poly.dto.TokenDTO;
import kopo.poly.util.CmmUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;


@Slf4j
@Component
@RefreshScope
@RequiredArgsConstructor
public class JwtTokenProvider {

    @Value("${jwt.secret.key}")
    private String secretKey;

    @Value("${jwt.token.access.name}")
    private String accessTokenName;

    /**
     * JWT 토큰(Access Token, Refresh Token)에 저장된 값 가져오기
     *
     * @param token 토큰
     * @return 회원 아이디(ex. hglee67)
     */
    public TokenDTO getTokenInfo(String token) {

        log.info(this.getClass().getName() + ".getTokenInfo Start!");

        // JWT 토큰 정보
        Claims claims = Jwts.parser().setSigningKey(secretKey).parseClaimsJws(token).getBody();

        String userId = CmmUtil.nvl(claims.getSubject());
        String role = CmmUtil.nvl((String) claims.get("roles")); // LoginService 생성된 토큰의 권한명과 동일

        log.info("userId : " + userId);
        log.info("role : " + role);

        TokenDTO pDTO = new TokenDTO();

        pDTO.setUserId(userId);
        pDTO.setRole(role);

        log.info(this.getClass().getName() + ".getTokenInfo End!");

        return pDTO;
    }

    /**
     * 쿠기에 저장된 JWT 토큰(Access Token) 가져오기
     *
     * @param request request 정보
     * @return 쿠기에 저장된 토큰 값
     */
    public String resolveToken(HttpServletRequest request) {

        log.info(this.getClass().getName() + ".resolveToken Start!");

        String token = "";

        // Cookie에 저장된 데이터 모두 가져오기
        Cookie[] cookies = request.getCookies();

        if (cookies != null) { // Cookie가 존재하면, Cookie에서 토큰 값 가져오기
            for (Cookie key : request.getCookies()) {
                if (key.getName().equals(accessTokenName)) {
                    token = CmmUtil.nvl(key.getValue());
                    break;
                }
            }
        }

        log.info(this.getClass().getName() + ".resolveToken End!");
        return token;
    }

}
