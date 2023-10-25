package kopo.poly.handler;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorMsg {

    ERR100("Do Not Login"),

    ERR300("Access and Refresh Token Empty"),
    ERR310("Token Error"),
    ERR320("Not Valid Refresh Token"),
    ERR330("Not Valid Token"),
    ERR400("Access Token Empty"),
    ERR410("Access Token Expired"),
    ERR500("Refresh Token Empty"),
    ERR510("Refresh Token Expired"),
    ERR600("Auth Error[AccessDeniedException]");

    private final String value;
}
