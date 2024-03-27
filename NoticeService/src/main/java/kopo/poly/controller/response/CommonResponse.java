package kopo.poly.controller.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;

@Getter
@Setter
public class CommonResponse<T> {

    private HttpStatus httpStatus;
    private String message;
    private T data;

    @Builder
    public CommonResponse(HttpStatus httpStatus, String message, T data) {
        this.httpStatus = httpStatus;
        this.message = message;
        this.data = data;
    }

    public static <T> CommonResponse<T> of(HttpStatus httpStatus, String message, T data) {
        return new CommonResponse<>(httpStatus, message, data);
    }


    public static ResponseEntity<CommonResponse> getErrors(BindingResult bindingResult) {
        return ResponseEntity.badRequest()
                .body(CommonResponse.of(HttpStatus.BAD_REQUEST,
                        HttpStatus.BAD_REQUEST.series().name(),
                        bindingResult.getAllErrors()));
    }

}
