package kopo.poly.service;

import kopo.poly.dto.UserInfoDTO;
import org.springframework.security.core.userdetails.UserDetailsService;

public interface IUserInfoService extends UserDetailsService {

    // 회원 가입하기(회원정보 등록하기)
    int insertUserInfo(UserInfoDTO pDTO);

    // 본인 회원 정보 조회
    UserInfoDTO getUserInfo(UserInfoDTO pDTO) throws Exception;
}
