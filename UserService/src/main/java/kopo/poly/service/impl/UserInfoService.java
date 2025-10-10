package kopo.poly.service.impl;

import kopo.poly.auth.AuthInfo;
import kopo.poly.dto.UserInfoDTO;
import kopo.poly.repository.UserInfoRepository;
import kopo.poly.repository.entity.UserInfoEntity;
import kopo.poly.service.IUserInfoService;
import kopo.poly.util.CmmUtil;
import kopo.poly.util.DateUtil;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
@Service
public class UserInfoService implements IUserInfoService {

    // UserInfoRepository를 주입받아 DB와 연동합니다.
    private final UserInfoRepository userInfoRepository;

    /**
     * 사용자 인증 정보 조회 (Spring Security에서 사용)
     * - userId로 DB에서 사용자 정보를 조회합니다.
     * - 조회된 정보를 DTO로 변환 후, 인증 객체(AuthInfo)로 반환합니다.
     * - 아이디가 없으면 UsernameNotFoundException을 발생시킵니다.
     *
     * @param userId 사용자 아이디
     * @return 인증 정보(UserDetails)
     */
    @SneakyThrows
    @Override
    public UserDetails loadUserByUsername(String userId) throws UsernameNotFoundException {
        log.info("{}.loadUserByUsername Start!", this.getClass().getName());
        UserInfoEntity rEntity = userInfoRepository.findByUserId(userId)
                .orElseThrow(() -> new UsernameNotFoundException(userId + " Not Found User"));

        UserInfoDTO rDTO = UserInfoDTO.from(rEntity);

        log.info("{}.loadUserByUsername End!", this.getClass().getName());
        return new AuthInfo(rDTO);
    }

    /**
     * 회원가입 처리
     * - 입력받은 DTO의 userId로 중복 여부를 확인합니다.
     * - 중복이 없으면 회원 정보를 Entity로 변환하여 DB에 저장합니다.
     * - 저장 후 DB에 정상적으로 등록되었는지 확인하여 결과를 반환합니다.
     *
     * @param pDTO 회원가입 요청 DTO
     * @return 1: 성공, 2: 아이디 중복, 0: 입력값 오류 또는 저장 실패
     */
    @Override
    public int insertUserInfo(UserInfoDTO pDTO) {
        log.info("{}.insertUserInfo Start!", this.getClass().getName());
        log.info("pDTO : {}", pDTO);

        String userId = CmmUtil.nvl(pDTO.userId());
        if (userId.isEmpty()) return 0; // 아이디가 없으면 실패 반환

        if (userInfoRepository.findByUserId(userId).isPresent()) {
            return 2; // 아이디 중복
        }
        UserInfoEntity pEntity = UserInfoEntity.builder()
                .userId(userId)
                .userName(CmmUtil.nvl(pDTO.userName()))
                .password(CmmUtil.nvl(pDTO.password()))
                .email(CmmUtil.nvl(pDTO.email()))
                .addr1(CmmUtil.nvl(pDTO.addr1()))
                .addr2(CmmUtil.nvl(pDTO.addr2()))
                .roles(CmmUtil.nvl(pDTO.roles()))
                .regId(userId).regDt(DateUtil.getDateTime("yyyy-MM-dd hh:mm:ss"))
                .chgId(userId).chgDt(DateUtil.getDateTime("yyyy-MM-dd hh:mm:ss"))
                .build();
        userInfoRepository.save(pEntity);

        log.info("{}.insertUserInfo End!", this.getClass().getName());

        return userInfoRepository.findByUserId(userId).isPresent() ? 1 : 0;
    }

    /**
     * 회원 정보 조회
     * - 입력받은 DTO의 userId로 DB에서 회원 정보를 조회합니다.
     * - 조회 결과가 있으면 DTO로 변환하여 반환하고, 없으면 null을 반환합니다.
     *
     * @param pDTO 회원 정보 조회 요청 DTO
     * @return 조회된 회원 정보 DTO (없으면 null)
     */
    @SneakyThrows
    @Override
    public UserInfoDTO getUserInfo(UserInfoDTO pDTO) {
        log.info("{}.getUserInfo Start!", this.getClass().getName());

        String userId = CmmUtil.nvl(pDTO.userId());
        log.info("getUserInfo | userId: {}", userId);

        UserInfoDTO rDTO = null;

        Optional<UserInfoEntity> rEntity = userInfoRepository.findByUserId(userId);

        if (rEntity.isPresent()) {
            rDTO = UserInfoDTO.from(rEntity.get());
        }

        log.info("{}.getUserInfo End! (not found)", this.getClass().getName());
        return rDTO;
    }
}
