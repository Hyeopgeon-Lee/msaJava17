// API 서버 정보 기입
const apiServer = "10.96.1.20:12000";
const loginPage = "/ss/login.html";
const jwtTokenName = "jwtAccessToken";

function loginCheck() {
    let token = $.cookie(jwtTokenName);

    alert("token : " + $.cookie());
    alert("token : " + $.cookie("jwtAccessToken"));
    alert("token : " + $.cookie("jwtAccessToken"));
    if (token === undefined || token == null) {
        alert("로그인이 되지 않았습니다. 로그인하길 바랍니다.");
        // location.href = loginPage;

    }
}

//글자 길이 바이트 단위로 체크하기(바이트값 전달)
function calBytes(str) {
    let tcount = 0;
    let tmpStr = String(str);
    let strCnt = tmpStr.length;
    let onechar;
    for (let i = 0; i < strCnt; i++) {
        onechar = tmpStr.charAt(i);
        if (escape(onechar).length > 4) {
            tcount += 2;
        } else {
            tcount += 1;
        }
    }
    return tcount;
}

