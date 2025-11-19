"use strict";

/* =========================
 * 기본 설정
 * ========================= */
// apiServer: API Gateway 서버 주소를 지정합니다. 실제 서비스 환경에 맞게 변경할 수 있습니다.
// const apiServer = "localhost:9000";
const apiServer = "api.k-bigdata.kr";
// loginPage: 인증이 필요한 페이지 접근 시 이동할 로그인 페이지 경로입니다.
const loginPage = "/ss/login.html";

// API_BASE: 현재 페이지의 프로토콜(http/https)에 맞춰 API Gateway의 전체 주소를 만듭니다.
const API_BASE = location.protocol + "//" + apiServer;

// $.ajaxSetup: jQuery의 ajax 요청에 대해 기본 설정을 적용합니다.
// - xhrFields.withCredentials: 서버가 세션/HttpOnly 쿠키를 사용하는 경우, 쿠키를 항상 포함하여 요청합니다.
// - contentType: 요청 데이터 타입을 JSON으로 지정합니다.
// - dataType: 응답 데이터 타입을 JSON으로 지정합니다.
// - cache: 캐시를 사용하지 않고 항상 최신 데이터를 요청합니다.
$.ajaxSetup({
    xhrFields: {withCredentials: true},
    contentType: "application/json; charset=UTF-8",
    dataType: "json",
    cache: false
});

/* URL 헬퍼 함수
 * - API 경로(path)를 받아서 전체 URL로 변환합니다.
 * - 경로가 '/'로 시작하면 바로 붙이고, 아니면 '/'를 추가해서 붙입니다.
 * 예시: apiUrl('/user') => 'http://localhost:9000/user'
 */
function apiUrl(path) {
    return path.charAt(0) === "/" ? API_BASE + path : API_BASE + "/" + path;
}

/* =========================
 * 에러 메시지 추출 함수
 * - 서버에서 받은 에러 응답(jqXHR)에서 사용자에게 보여줄 메시지를 추출합니다.
 * - 서버 표준 포맷(JsonResponseWriter)에 맞춰 처리합니다.
 * - r.data.msg: 서버에서 상세 메시지를 제공할 때 사용합니다.
 * - r.message: 서버에서 일반 메시지를 제공할 때 사용합니다.
 * - 위 조건에 해당하지 않으면 기본 에러 메시지를 반환합니다.
 */
function extractErrorMessageFromXhr(jqXHR) {
    try {
        const r = jqXHR.responseJSON;
        if (r && r.data && r.data.msg) return r.data.msg;  // 서버가 상세 메시지 제공
        if (r && r.message) return r.message;              // 서버가 일반 메시지 제공
    } catch (e) {
        // 파싱 실패 시 무시하고 기본 메시지 반환
    }
    return "요청 처리 중 오류가 발생했습니다.";
}

/* =========================
 * 인증 상태 조회 함수(API)
 * - 서버의 로그인 상태 조회 엔드포인트를 호출해 인증/미인증을 판단합니다.
 * - 응답 예시:
 *   200 OK
 *   {
 *     "status": 200,
 *     "message": "OK",
 *     "data": { "userId": "kopo123", "userName": "홍길동", ... } // 로그인됨
 *   }
 *   또는 data: {} / null // 로그인 안됨
 * - withCredentials: 서버가 세션/HttpOnly 쿠키를 사용하는 경우 필요합니다.
 *   프론트엔드는 쿠키를 직접 읽거나 쓰지 않습니다.
 */
function fetchLoginInfo() {
    return $.ajax({
        url: apiUrl("/login/v1/loginInfo"), // 로그인 정보 조회 API 엔드포인트
        method: "GET",                      // GET 방식으로 요청
        dataType: "json",                   // 응답을 JSON으로 받음
        xhrFields: {withCredentials: true}   // 쿠키 포함
    });
}

/* =========================
 * 로그인 가드 함수
 * - 로그인 필수 화면에서 사용합니다.
 * - 인증 성공 시 onAuthed(data) 콜백을 실행합니다.
 * - 인증 실패 또는 오류 시 onUnauthed() 콜백을 실행하고, 필요시 로그인 페이지로 이동합니다.
 * - options:
 *   - redirect: 인증 실패 시 로그인 페이지로 이동 여부 (기본값 true)
 *   - onAuthed: 인증 성공 시 실행할 함수
 *   - onUnauthed: 인증 실패 시 실행할 함수
 */
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
            // 로그인 여부 판단: userId, id, username 중 하나라도 있으면 로그인된 것으로 간주합니다.
            const authed = !!(data && (data.userId));
            if (authed) {
                opts.onAuthed(data); // 인증 성공 콜백 실행
            } else {
                opts.onUnauthed();   // 인증 실패 콜백 실행
                // if (opts.redirect) location.href = loginPage; // 필요시 로그인 페이지로 이동(현재는 주석 처리)
            }
        })
        .fail(function () {
            // API 호출 자체가 실패한 경우
            opts.onUnauthed(); // 인증 실패 콜백 실행
            if (opts.redirect) location.href = loginPage; // redirect 옵션이 true면 로그인 페이지로 이동
        });
}

/* =========================
 * (선택) 로그인 여부만 확인(리디렉션 없음)
 * - 로그인 여부에 따라 UI만 토글할 때 사용합니다.
 * - 인증 성공/실패에 따라 각각 콜백을 실행합니다.
 * - options:
 *   - onAuthed: 인증 성공 시 실행할 함수
 *   - onUnauthed: 인증 실패 시 실행할 함수
 */
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
            // userId가 있으면 로그인된 것으로 간주합니다.
            const authed = !!(data && data.userId);
            authed ? opts.onAuthed(data) : opts.onUnauthed(); // 인증 성공/실패에 따라 콜백 실행
        })
        .fail(function () {
            // API 호출 실패 시 인증 실패 콜백 실행
            opts.onUnauthed();
        });
}
