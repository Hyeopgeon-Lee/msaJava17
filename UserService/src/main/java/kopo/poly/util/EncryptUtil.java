package kopo.poly.util;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;

/**
 * 암호화 유틸리티 클래스
 * - SHA-256 해시 암호화
 * - AES-128 CBC 대칭키 암호화/복호화
 */
public class EncryptUtil {

    /**
     * SHA-256 해시 암호화 시 사용할 추가 문자열 (Salt 개념)
     * - 동일한 입력값이라도 암호화 결과를 다르게 하기 위함
     */
    private static final String addMessage = "PolyDataAnalysis";

    /**
     * AES CBC 모드에서 사용할 초기 벡터(IV) - 16바이트
     * - 암호화 초기화 값으로 동일해야 복호화 가능
     */
    private static final byte[] ivBytes = new byte[16];

    /**
     * AES-128 CBC 암호화 시 사용할 키 (16바이트 = 128비트)
     * - 16자 이내로 지정해야 AES-128 CBC 키 조건 충족
     */
    private static final String key = "PolyTechnic12345";

    /**
     * SHA-256 해시 함수로 문자열 암호화 (단방향)
     *
     * @param str 암호화할 원문
     * @return 암호화된 64자리 문자열 (Hex 포맷)
     */
    public static String encHashSHA256(String str) {
        String result;
        String plainText = addMessage + str;

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(plainText.getBytes());
            byte[] hash = digest.digest();

            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }

            result = sb.toString();

        } catch (NoSuchAlgorithmException e) {
            result = ""; // SHA-256 지원되지 않으면 빈 문자열
        }

        return result;
    }

    /**
     * AES-128 CBC 방식으로 문자열 암호화
     *
     * @param str 평문 문자열
     * @return Base64로 인코딩된 암호문
     */
    public static String encAES128CBC(String str)
            throws NoSuchAlgorithmException, NoSuchPaddingException,
            InvalidKeyException, InvalidAlgorithmParameterException,
            IllegalBlockSizeException, BadPaddingException {

        byte[] textBytes = str.getBytes(StandardCharsets.UTF_8);

        SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(ivBytes);

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);

        byte[] encrypted = cipher.doFinal(textBytes);

        return Base64.getEncoder().encodeToString(encrypted);
    }

    /**
     * AES-128 CBC 방식으로 암호문 복호화
     *
     * @param str Base64로 인코딩된 암호문
     * @return 복호화된 평문 문자열
     */
    public static String decAES128CBC(String str)
            throws NoSuchAlgorithmException, NoSuchPaddingException,
            InvalidKeyException, InvalidAlgorithmParameterException,
            IllegalBlockSizeException, BadPaddingException {

        byte[] encryptedBytes = Base64.getDecoder().decode(str);

        SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(ivBytes);

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);

        byte[] decrypted = cipher.doFinal(encryptedBytes);

        return new String(decrypted, StandardCharsets.UTF_8);
    }
}