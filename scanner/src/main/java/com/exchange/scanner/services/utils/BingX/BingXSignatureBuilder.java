package com.exchange.scanner.services.utils.BingX;


import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

@Slf4j
public class BingXSignatureBuilder {

    private final String timestamp;

    private static final String ALGORITHM = "HmacSHA256";

    @Getter
    private String signature;

    @Getter
    private TreeMap<String, String> parameters;

    @Getter
    private Map<String, String> headers;

    private final String apiKey;

    private final String secretApi;

    public BingXSignatureBuilder(String apiKey, String secretApi, TreeMap<String, String> parameters) {
        this.timestamp = String.valueOf(System.currentTimeMillis());
        this.apiKey = apiKey;
        this.secretApi = secretApi;
        parameters.put("timestamp", timestamp);
        this.parameters = parameters;
        this.headers = createHeaders();
    }

    public void createSignature() {
        String preSignString = createPreSignString();

        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(secretApi.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            mac.init(keySpec);
            byte[] hash = mac.doFinal(preSignString.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            signature = hexString.toString();

        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            log.error("Ошибка генерации подписи запроса для BingX. Причина: {}", ex.getLocalizedMessage());
        }
    }

    private Map<String, String> createHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-BX-APIKEY", apiKey);
        headers.put("User-Agent","Mozilla/5.0");

        return headers;
    }

    private String createPreSignString() {
        StringBuilder sb = new StringBuilder();
        parameters.forEach((key, value) -> {
            sb.append(key).append("=").append(value).append("&");
        });
        sb.deleteCharAt(sb.length() - 1);

        return sb.toString();
    }
}
