package com.exchange.scanner.services.utils.Huobi;

import lombok.Getter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class HuobiSignatureBuilder {

    private static final String ALGORITHM = "HmacSHA256";

    private static final int SIGNATURE_VERSION = 2;

    private final String timestamp;

    private final String key;

    private final String secret;

    private final String path;

    private final String method;

    private TreeMap<String, String> params;

    @Getter
    private String signature;

    public HuobiSignatureBuilder(String key, String secret, String path, String method, TreeMap<String, String> params) {
        this.timestamp = createTimestamp();
        this.key = key;
        this.secret = secret;
        this.path = path;
        this.method = method;
        this.params = params;
    }

    public void createSignature() {
        String paramsString = createParamsString();
        String preSignText = method + "\n" +
                "api.huobi.pro" + "\n" +
                path + "\n" +
                paramsString;

        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            mac.init(secretKey);
            byte[] hash = mac.doFinal(preSignText.getBytes(StandardCharsets.UTF_8));
            signature = Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }

    private String createParamsString() {
        StringBuilder sb = new StringBuilder();
        params.put("AccessKeyId", key);
        params.put("SignatureMethod", ALGORITHM);
        params.put("SignatureVersion", SIGNATURE_VERSION + "");
        params.put("Timestamp", timestamp);

        params.forEach((k, v) -> sb.append(k).append("=").append(URLEncoder.encode(v, StandardCharsets.UTF_8)).append("&"));
        sb.deleteCharAt(sb.length() - 1);
        return sb.toString();
    }

    private String createTimestamp() {
        return ZonedDateTime.now(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.SECONDS)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
    }
}
