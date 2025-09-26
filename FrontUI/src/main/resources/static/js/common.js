"use strict";

/* =========================
 * 기본 설정
 * ========================= */
// 게이트웨이
const apiServer = "localhost:9000";
// 로그인 페이지
const loginPage = "/ss/login.html";

// 프로토콜 자동 맞춤(http/https)
const API_BASE = location.protocol + "//" + apiServer;

// 전역 기본값(한 번만)
$.ajaxSetup({
    xhrFields: {withCredentials: true},          // RT/AT 쿠키 항상 전송
    contentType: "application/json; charset=UTF-8",
    dataType: "json",
    cache: false
});

/* URL 헬퍼 */
function apiUrl(path) {
    return path.charAt(0) === "/" ? API_BASE + path : API_BASE + "/" + path;
}

/* =========================
 * 에러 메시지 추출 (서버 표준 JsonResponseWriter 포맷 대응)
 * ========================= */
function extractErrorMessageFromXhr(jqXHR) {
    try {
        const r = jqXHR.responseJSON;
        if (r && r.data && r.data.msg) return r.data.msg;  // MsgDTO.msg
        if (r && r.message) return r.message;              // "CLIENT_ERROR" 등
    } catch (e) {
    }
    return "요청 처리 중 오류가 발생했습니다.";
}

/* =========================
 * 인증 상태 조회(API)
 * - 서버의 로그인 상태 조회 엔드포인트를 호출해 인증/미인증 판단
 * - 응답 예시:
 *   200 OK
 *   {
 *     "status": 200,
 *     "message": "OK",
 *     "data": { "userId": "kopo123", "userName": "홍길동", ... } // 로그인됨
 *   }
 *   또는 data: {} / null // 로그인 안됨
 * ========================= */
function fetchLoginInfo() {
    // withCredentials는 서버가 세션/HttpOnly 쿠키를 쓰는 경우에만 필요
    // (프론트는 쿠키를 직접 읽거나 쓰지 않음)
    return $.ajax({
        url: apiUrl("/login/v1/loginInfo"),
        method: "GET",
        dataType: "json",
        xhrFields: {withCredentials: true}
    });
}

/* =========================
 * 로그인 가드
 * - 로그인 필수 화면에서 호출
 * - 인증 성공: onAuthed(data) 호출
 * - 미인증/오류: 로그인 페이지로 이동(redirect=true일 때)
 * ========================= */
function requireLogin(options) {
    const opts = $.extend({
        redirect: true,
        onAuthed: function (/* data */) {
        },
        onUnauthed: function () {
        }
    }, options);

    return fetchLoginInfo()
        .done(function (res) {
            const data = res && res.data;
            // "로그인되면, API 호출결과에 아이디 등 값이 들어오고, 없으면 값이 안와"
            const authed = !!(data && (data.userId || data.id || data.username));
            if (authed) {
                opts.onAuthed(data);
            } else {
                opts.onUnauthed();
                // if (opts.redirect) location.href = loginPage;
            }
        })
        .fail(function () {
            // 조회 자체가 실패해도 보호 페이지에서는 로그인 화면으로 보냄
            opts.onUnauthed();
            // if (opts.redirect) location.href = loginPage;
        });
}

/* =========================
 * (선택) 로그인 여부만 확인(리디렉션 없음)
 * - 로그인 여부에 따라 UI만 토글할 때 사용
 * ========================= */
function checkLoginSilently(options) {
    const opts = $.extend({
        onAuthed: function (/* data */) {
        },
        onUnauthed: function () {
        }
    }, options);

    return fetchLoginInfo()
        .done(function (res) {
            const data = res && res.data;
            const authed = !!(data && data.userId);
            authed ? opts.onAuthed(data) : opts.onUnauthed();
        })
        .fail(function () {
            opts.onUnauthed();
        });
}

/* =========================
 * (예시) 보호 페이지에서 사용
 * ========================= */
// $(function() {
//   requireLogin({
//     onAuthed: function(user) {
//       // 예: 화면 상단에 사용자명 표시
//       $("#loginUserId").text(user.userId || user.id || user.username);
//     }
//   });
// });


