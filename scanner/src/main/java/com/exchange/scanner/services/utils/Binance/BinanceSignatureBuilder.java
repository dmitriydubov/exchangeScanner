package com.exchange.scanner.services.utils.Binance;


import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class BinanceSignatureBuilder {

    private static final String ALGORITHM = "HmacSHA256";

    private final String timestamp;

    private final String apiKey;

    private final String secretKey;

    private String signature;

    @Getter
    private Map<String, String> parameters;

    @Getter
    private Map<String, String> headers;

    public BinanceSignatureBuilder(String apiKey, String secretKey, Map<String, String> parameters) {
        this.timestamp = String.valueOf(System.currentTimeMillis());
        this.apiKey = apiKey;
        this.secretKey = secretKey;
        parameters.put("timestamp", this.timestamp);
        this.parameters = parameters;
        this.headers = createHeaders();
    }

    public void createSignature() {
        String payload = createPayload();

        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            SecretKeySpec key = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            mac.init(key);
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            signature = hexString.toString();
            parameters.put("signature", signature);

        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            log.error("Ошибка генерации подписи запроса для Binance. Причина: {}", ex.getLocalizedMessage());
        }
    }

    private Map<String, String> createHeaders() {
        Map<String, String> initialHeaders = new HashMap<>();
        initialHeaders.put("X-MBX-APIKEY", this.apiKey);

        return initialHeaders;
    }

    private String createPayload() {
        StringBuilder sb = new StringBuilder();
        parameters.forEach((key, value) -> sb.append(key).append("=").append(value).append("&"));
        sb.deleteCharAt(sb.length() - 1);

        return sb.toString();
    }
}
