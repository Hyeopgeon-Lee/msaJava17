package kopo.poly.auth;


import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import kopo.poly.dto.TokenDTO;
import kopo.poly.util.CmmUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.util.Date;

@Slf4j
@RefreshScope
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    @Value("${jwt.secret.key}")
    private String secretKey;

    @Value("${jwt.token.creator}")
    private String creator;

    @Value("${jwt.token.access.valid.time}")
    private long accessTokenValidTime;

    @Value("${jwt.token.access.name}")
    private String accessTokenName;

    @Value("${jwt.token.refresh.valid.time}")
    private long refreshTokenValidTime;

    @Value("${jwt.token.refresh.name}")
    private String refreshTokenName;

    public static final String HEADER_PREFIX = "Bearer "; // Bearer 토큰 사용을 위한 선언 값

    /**
     * JWT 토큰(Access Token, Refresh Token)생성
     *
     * @param userId    회원 아이디(ex. hglee67)
     * @param roles     회원 권한
     * @param tokenType token 유형
     * @return 인증 처리한 정보(로그인 성공, 실패)
     */
    public String createToken(String userId, String roles, JwtTokenType tokenType) {

        log.info(this.getClass().getName() + ".createToken Start!");

        log.info("userId : " + userId);


        long validTime = 0;

        if (tokenType == JwtTokenType.ACCESS_TOKEN) { // Access Token이라면
            validTime = (accessTokenValidTime);

        } else if (tokenType == JwtTokenType.REFRESH_TOKEN) { // Refresh Token이라면
            validTime = (refreshTokenValidTime);

        }

        Claims claims = Jwts.claims()
                .setIssuer(creator) // JWT 토큰 생성자 기입함
                .setSubject(userId); // 회원아이디 저장 : PK 저장(userId)

        claims.put("roles", roles); // JWT Paylaod에 정의된 기본 옵션 외 정보를 추가 - 사용자 권한 추가
        Date now = new Date();

        log.info(this.getClass().getName() + ".createToken End!");

        // 보안키 문자들을 JWT Key 형태로 변경하기
        SecretKey secret = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretKey));

        // Builder를 통해 토큰 생성
        return Jwts.builder()
                .setClaims(claims) // 정보 저장
                .setIssuedAt(now) // 토큰 발행 시간 정보
                .setExpiration(new Date(now.getTime() + (validTime * 1000))) // set Expire Time
                .signWith(secret, SignatureAlgorithm.HS256)  // 사용할 암호화 알고리즘과
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

        // 보안키 문자들을 JWT Key 형태로 변경하기
        SecretKey secret = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretKey));

        // JWT 토큰 정보
        Claims claims = Jwts.parserBuilder().setSigningKey(secret).build().parseClaimsJws(token).getBody();

        String userId = CmmUtil.nvl(claims.getSubject());
        String role = CmmUtil.nvl((String) claims.get("roles")); // LoginService 생성된 토큰의 권한명과 동일

        log.info("userId : " + userId);
        log.info("role : " + role);

        // TokenDTO는 자바17의 Record 객체 사용했기에 빌더패턴 적용함
        TokenDTO rDTO = TokenDTO.builder().userId(userId).role(role).build();

        log.info(this.getClass().getName() + ".getTokenInfo End!");

        return rDTO;
    }

    /**
     * 쿠기 및 인증해더(Bearer) 저장된 JWT 토큰(Access Token, Refresh Token) 가져오기
     *
     * @param request   request 정보
     * @param tokenType token 유형
     * @return 쿠기에 저장된 토큰 값
     */
    public String resolveToken(HttpServletRequest request, JwtTokenType tokenType) {

        log.info(this.getClass().getName() + ".resolveToken Start!");

        String tokenName = "";

        if (tokenType == JwtTokenType.ACCESS_TOKEN) { // Access Token이라면
            tokenName = accessTokenName;

        } else if (tokenType == JwtTokenType.REFRESH_TOKEN) { // Refresh Token이라면
            tokenName = refreshTokenName;

        }

        String token = "";

        // Cookie에 저장된 데이터 모두 가져오기
        Cookie[] cookies = request.getCookies();

        if (cookies != null) { // Cookie가 존재하면, Cookie에서 토큰 값 가져오기
            for (Cookie key : request.getCookies()) {

                log.info("cookies 이름 : " + key.getName());
                if (key.getName().equals(tokenName)) {
                    token = CmmUtil.nvl(key.getValue());
                    break;
                }
            }
        }

        // Cookies에 토큰이 존재하지 않으면, Beaerer 토큰에 값이 있는지 확인함
        if (token.length() == 0) {
            String bearerToken = request.getHeader(HttpHeaders.AUTHORIZATION);

            log.info("bearerToken : " + bearerToken);
            if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(HEADER_PREFIX)) {
                token = bearerToken.substring(7);
            }

            log.info("bearerToken token : " + token);

        }

        log.info(this.getClass().getName() + ".resolveToken End!");
        return token;
    }

}

