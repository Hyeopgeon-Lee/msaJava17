// API 서버 정보 기입
const apiServer = "localhost:13000";
const loginPage = "/ss/login.html";
const jwtTokenName = "jwtAccessToken";

function loginCheck() {
    let token = $.cookie(jwtTokenName);

    if (token == "undefined" || token == null) {
        alert("로그인이 되지 않았습니다. 로그인하길 바랍니다.");
        location.href = loginPage;

    }
}