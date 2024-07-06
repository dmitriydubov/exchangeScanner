package com.exchange.scanner.services.utils.OKX;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Map;

@Slf4j
public class OKXSignatureBuilder {

    private final String ALGORITHM;

    private final String secretKey;

    @Getter
    private final String timestamp;

    @Getter
    private String signature;

    public OKXSignatureBuilder(String secretKey) {
        this.secretKey = secretKey;
        this.ALGORITHM = "HmacSHA256";
        this.timestamp = ZonedDateTime.now().truncatedTo(ChronoUnit.MILLIS).format(DateTimeFormatter.ISO_INSTANT);
    }

    public void createSignature(String method, String path,  Map<String, String> parameters) {
        String preSignString = getPreSignString(method, path, parameters);

        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            SecretKeySpec key = new SecretKeySpec(secretKey.getBytes(), ALGORITHM);
            mac.init(key);
            byte[] hash = mac.doFinal(preSignString.getBytes(StandardCharsets.UTF_8));
            signature = Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            log.error("Ошибка генерации подписи запроса для OKX. Причина: {}", ex.getLocalizedMessage());
        }

    }

    private String getPreSignString(String method, String path, Map<String, String> parameters) {
        StringBuilder sb = new StringBuilder();
        sb.append(this.timestamp);
        sb.append(method);
        sb.append(path);
        sb.append("?");
        parameters.forEach((key, value) -> {
            sb.append(key).append("=").append(value).append("&");
        });
        sb.deleteCharAt(sb.length() - 1);
        return sb.toString();
    }
}
