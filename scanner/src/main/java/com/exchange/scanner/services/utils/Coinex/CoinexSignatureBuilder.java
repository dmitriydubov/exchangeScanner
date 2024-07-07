package com.exchange.scanner.services.utils.Coinex;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

@Slf4j
public class CoinexSignatureBuilder {

    @Getter
    private final String timestamp;

    private final String apiSecret;

    private static final String ALGORITHM = "HmacSHA256";

    @Getter
    private String signature;

    @Getter
    private Map<String, String> parameters;

    public CoinexSignatureBuilder(String apiSecret, Map<String, String> parameters) {
        this.timestamp = String.valueOf(System.currentTimeMillis());
        this.apiSecret = apiSecret;
        this.parameters = parameters;
    }

    public void createSignature(String method, String path) {
        String preparedString = createPreparedString(method, path);

        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            SecretKeySpec key = new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            mac.init(key);
            byte[] hash = mac.doFinal(preparedString.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            signature = hexString.toString().toLowerCase();
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            log.error("Ошибка генерации подписи запроса для LBank. Причина: {}", ex.getLocalizedMessage());
        }
    }

    private String createPreparedString(String method, String path) {
        StringBuilder sb = new StringBuilder();
        sb.append(method).append(path);
        parameters.forEach((key, value) -> sb.append("?").append(key).append("=").append(value).append("&"));
        sb.deleteCharAt(sb.length() - 1);
        sb.append(timestamp);
        return sb.toString();
    }
}
