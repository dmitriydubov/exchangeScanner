package com.exchange.scanner.services.utils.Poloniex;

import lombok.Getter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.TreeMap;

public class PoloniexSignatureBuilder {

    private static final String ALGORITHM = "HmacSHA256";

    private final String secret;

    @Getter
    private final long timestamp;

    private final String method;

    private final String path;

    @Getter
    private String signature;

    private TreeMap<String, String> parameters;

    public PoloniexSignatureBuilder(String secret, String method, String path, TreeMap<String, String> parameters) {
        this.secret = secret;
        this.timestamp = System.currentTimeMillis();
        this.method = method;
        this.path = path;
        this.parameters = parameters;
    }

    public void createSignature() {
        String paramsString = createParamsString();
        String preSignString = method + "\n" +
                path + "\n" +
                paramsString;
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            mac.init(secretKey);
            byte[] hash = mac.doFinal(preSignString.getBytes(StandardCharsets.UTF_8));
            signature = Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }

    private String createParamsString() {
        parameters.put("signTimestamp", String.valueOf(timestamp));
        StringBuilder sb = new StringBuilder();
        parameters.forEach((key, value) -> sb.append(key).append("=").append(URLEncoder.encode(value, StandardCharsets.UTF_8)).append("&"));
        sb.deleteCharAt(sb.length() - 1);
        return sb.toString();
    }
}
