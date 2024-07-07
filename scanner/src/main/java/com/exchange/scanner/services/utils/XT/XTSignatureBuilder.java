package com.exchange.scanner.services.utils.XT;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.TreeMap;

@Slf4j
public class XTSignatureBuilder {

    private static final String ALGORITHM = "HmacSHA256";

    @Getter
    private final String timestamp;

    private final String apiKey;

    private final String secretKey;

    @Getter
    private String signature;

    @Getter
    private TreeMap<String, String> parameters;

    @Getter
    private TreeMap<String, String> headers;

    public XTSignatureBuilder(String apiKey, String secretKey, TreeMap<String, String> parameters) {
        this.timestamp = String.valueOf(System.currentTimeMillis());
        this.apiKey = apiKey;
        this.secretKey = secretKey;
        this.parameters = parameters;
        this.headers = createHeaders();
    }

    public void createSignature(String method, String path) {
        String preSignString = createPreSignString(method, path);

        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            mac.init(keySpec);
            byte[] hash = mac.doFinal(preSignString.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexToString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexToString.append('0');
                hexToString.append(hex);
            }

            signature = hexToString.toString();
            headers.put("validate-signature", signature);
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            log.error("Ошибка генерации подписи запроса для Gate.io. Причина: {}", ex.getLocalizedMessage());
        }
    }

    private String createPreSignString(String method, String path) {
        StringBuilder y = new StringBuilder();
        y.append("#").append(method).append("#").append(path);
        if (!parameters.isEmpty()) {
            y.append("#");
            parameters.forEach((key, value) -> {

                y.append(key).append("=").append(value).append("&");
            });
            y.deleteCharAt(y.length() - 1);
        }

        StringBuilder x = new StringBuilder();
        headers.forEach((key, value) -> {
            x.append(key).append("=").append(value).append("&");
        });
        x.deleteCharAt(x.length() - 1);

        return x.toString() + y;
    }

    private TreeMap<String, String> createHeaders() {
        TreeMap<String, String> initialHeaders = new TreeMap<>();
        initialHeaders.put("validate-algorithms", "HmacSHA256");
        initialHeaders.put("validate-appkey", apiKey);
        initialHeaders.put("validate-recvwindow", "5000");
        initialHeaders.put("validate-timestamp", timestamp);

        return initialHeaders;
    }
}
