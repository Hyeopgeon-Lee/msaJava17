package kopo.poly.jwt;

import io.jsonwebtoken.*;
import kopo.poly.dto.TokenDTO;
import kopo.poly.util.CmmUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    @Value("${jwt.secret.key}")
    private String secretKey;

    @Value("${jwtw.token.creator}")
    private String creator;

    @Value("${jwt.token.access.valid.time}")
    private long accessTokenValidTime;

    @Value("${jwt.token.access.name}")
    private String accessTokenName;

    @Value("${jwt.token.refresh.name}")
    private String refreshTokenName;

    /**
     * JWT 토큰(Access Token)생성
     *
     * @param userId 회원 아이디(ex. hglee67)
     * @param roles  회원 권한
     * @return 인증 처리한 정보(로그인 성공, 실패)
     */
    public String createToken(String userId, String roles) {

        log.info(this.getClass().getName() + ".createToken Start!");

        log.info("userId : " + userId);

        Claims claims = Jwts.claims()
                .setIssuer(creator) // JWT 토큰 생성자 기입함
                .setSubject(userId); // 회원아이디 저장 : PK 저장(userId)

        claims.put("roles", roles); // JWT Paylaod에 정의된 기본 옵션 외 정보를 추가 - 사용자 권한 추가
        Date now = new Date();

        log.info(this.getClass().getName() + ".createToken End!");

        // Builder를 통해 토큰 생성
        return Jwts.builder()
                .setClaims(claims) // 정보 저장
                .setIssuedAt(now) // 토큰 발행 시간 정보
                .setExpiration(new Date(now.getTime() + (accessTokenValidTime * 1000))) // set Expire Time
                .signWith(SignatureAlgorithm.HS256, secretKey)  // 사용할 암호화 알고리즘과
                .compact();
    }

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
     * JWT 토큰(Access Token, Refresh Token)에서 인증 정보 조회 및
     * 아이디, 패스워드가 맞다면, Spring Security를 통해 로그인처리하기
     *
     * @param token 토큰
     * @return 인증 처리한 정보(로그인 성공, 실패)
     */
    public Authentication getAuthentication(String token) {

        log.info(this.getClass().getName() + ".getAuthentication Start!");
        log.info("getAuthentication : " + token);

        TokenDTO rDTO = getTokenInfo(token); // 토큰에 저장된 정보 가져오기

        // JWT 토큰에 저장된 사용자 아이디 : hglee67
        String userId = CmmUtil.nvl(rDTO.getUserId());

        // JWT 토큰에 저장된 사용자 아이디 : ROLE_USER
        String roles = CmmUtil.nvl(rDTO.getRole());

        log.info("user_id : " + userId);
        log.info("roles : " + roles);

        Set<GrantedAuthority> pSet = new HashSet<>();
        if (roles.length() > 0) { //DB에 저장된 Role이 있는 경우에만 실행
            for (String role : roles.split(",")) {
                pSet.add(new SimpleGrantedAuthority(role));

            }
        }

        log.info(this.getClass().getName() + ".getAuthentication End!");

        // Spring Security가 로그인 성공된 정보를 Spring Security에서 사용하기 위해
        // Spring Security용 UsernamePasswordAuthenticationToken 생성
        return new UsernamePasswordAuthenticationToken(userId, "", pSet);
    }

    /**
     * 쿠기에 저장된 JWT 토큰(Access Token, Refresh Token) 가져오기
     *
     * @param request   request 정보
     * @param tokenType token 유형
     * @return 쿠기에 저장된 토큰 값
     */
    public String resolveToken(ServerHttpRequest request, JwtTokenType tokenType) {

        log.info(this.getClass().getName() + ".resolveToken Start!");

        String token = ""; // 토큰
        String tokenName = "";

        if (tokenType == JwtTokenType.ACCESS_TOKEN) { // Access Token이라면
            tokenName = accessTokenName;

        } else if (tokenType == JwtTokenType.REFRESH_TOKEN) { // Refresh Token이라면
            tokenName = refreshTokenName;

        }

        HttpCookie cookie = request.getCookies().getFirst(tokenName);

        if (cookie != null) {

            token = CmmUtil.nvl(cookie.getValue());

        }

        log.info(this.getClass().getName() + ".resolveToken End!");

        return token;
    }

    /**
     * JWT 토큰(Access Token, Refresh Token) 상태 확인
     *
     * @param token 토큰
     * @return 상태정보(EXPIRED, ACCESS, DENIED)
     */
    public JwtStatus validateToken(String token) {

        if (token.length() > 0) {

            try {
                Jws<Claims> claims = Jwts.parser().setSigningKey(secretKey).parseClaimsJws(token);

                // 토큰 만료여부 체크
                if (claims.getBody().getExpiration().before(new Date())) {
                    return JwtStatus.EXPIRED; // 기간 만료

                } else {
                    return JwtStatus.ACCESS; // 유효한 토큰
                }

            } catch (ExpiredJwtException e) {
                // 만료된 경우에는 refresh token을 확인하기 위해
                return JwtStatus.EXPIRED; // 혹시 몰라서 Exception으로 한번 더 체크 기간 만료

            } catch (JwtException | IllegalArgumentException e) {
                log.info("jwtException : {}", e);

                return JwtStatus.DENIED;
            }

        } else {
            return JwtStatus.DENIED;
        }

    }
}

