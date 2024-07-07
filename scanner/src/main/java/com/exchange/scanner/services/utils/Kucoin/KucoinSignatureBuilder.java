package com.exchange.scanner.services.utils.Kucoin;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@Slf4j
public class KucoinSignatureBuilder {

    public static String generateKucoinSignature(String secretKey, String strToSign) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(strToSign.getBytes(StandardCharsets.UTF_8));

            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            log.error("Ошибка генерации подписи запроса для Kucoin. Причина: {}", ex.getLocalizedMessage());
            return "";
        }
    }

    public static String generateKucoinPassphrase(String secretKey, String passphrase) {
        return generateKucoinSignature(secretKey, passphrase);
    }
}
