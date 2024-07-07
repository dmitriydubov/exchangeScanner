package com.exchange.scanner.services.utils.Bitmart;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

@Slf4j
public class BitmartSignatureBuilder {

    private static final String ALGORITHM = "HmacSHA256";

    private final String secretKey;

    private final String memo;

    @Getter
    private final String timestamp;

    @Getter
    private String signature;

    public BitmartSignatureBuilder(String secretKey, String memo) {
        this.secretKey = secretKey;
        this.memo = memo;
        this.timestamp = Long.toString(System.currentTimeMillis());
    }

    public void createSignature(String method, Map<String, String> parameters) {
        String paramsString = createParamsString(parameters);
        String preSignString = timestamp + "#" + memo + "#" + paramsString;
        generateSignature(preSignString);
    }

    public void createSignature(String method) {
        String preSignString = timestamp + "#" + memo;
        generateSignature(preSignString);
    }

    private String createParamsString(Map<String, String> parameters) {
        StringBuilder sb = new StringBuilder();

        parameters.forEach((key, value) -> sb.append(key).append("=").append(value).append("&"));
        sb.deleteCharAt(sb.length() - 1);
        return sb.toString();
    }

    private void generateSignature(String preSignString) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            SecretKeySpec key = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            mac.init(key);
            byte[] hash = mac.doFinal(preSignString.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            signature = hexString.toString();
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            log.error("Ошибка генерации подписи запроса для Bitmart. Причина: {}", ex.getLocalizedMessage());
        }
    }
}
