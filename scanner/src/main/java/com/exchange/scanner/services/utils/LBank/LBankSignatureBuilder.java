package com.exchange.scanner.services.utils.LBank;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

@Slf4j
@Getter
public class LBankSignatureBuilder {

    private final String apiKey;

    private final String apiSecret;

    private final String algorithm = "HmacSHA256";

    private final String timestamp;

    private final String echoStr;

    private String signature;

    private TreeMap<String, String> parameters;

    public LBankSignatureBuilder(String apiKey, String apiSecret, TreeMap<String, String> parameters) {
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.timestamp = String.valueOf(Instant.now().toEpochMilli());
        this.echoStr = EchoStrGenerator.generateEchoStr();
        this.parameters = parameters;
    }

    public void createSignature() {
        String paramsString = generatePreSignString();
        String preparedString = DigestUtils.md5Hex(paramsString).toUpperCase();
        signature = generateSignature(preparedString);
    }

    public TreeMap<String, String> getRequestParams() {
        parameters.put("sign", signature);
        return parameters;
    }

    private String generatePreSignString() {
        parameters.put("api_key", apiKey);
        parameters.put("echostr", echoStr);
        parameters.put("signature_method", algorithm);
        parameters.put("timestamp", timestamp);

        StringBuilder sb = new StringBuilder();

        parameters.forEach((key, value) -> {
            sb.append(key).append("=").append(value).append("&");
        });
        sb.deleteCharAt(sb.length() - 1);
        return sb.toString();
    }

    private String generateSignature(String preparedString) {
        try {
            Mac mac = Mac.getInstance(algorithm);
            SecretKeySpec secretKey = new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), algorithm);
            mac.init(secretKey);
            byte[] hash = mac.doFinal(preparedString.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            log.error("Ошибка генерации подписи запроса для LBank. Причина: {}", ex.getLocalizedMessage());
            return "";
        }
    }

    private static class EchoStrGenerator {
        private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        private static final int LENGTH = 32;

        public static String generateEchoStr() {
            Random random = new SecureRandom();
            StringBuilder echoStrBuilder = new StringBuilder(LENGTH);

            for (int i = 0; i < LENGTH; i++) {
                int index = random.nextInt(CHARACTERS.length());
                echoStrBuilder.append(CHARACTERS.charAt(index));
            }

            return echoStrBuilder.toString();
        }
    }
}
