package com.exchange.scanner.services.utils;

import com.exchange.scanner.dto.response.exchangedata.binance.depth.BinanceCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.bingx.depth.BingXCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.bitget.depth.BitgetCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.bitmart.depth.BitmartCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.bybit.depth.BybitCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.coinex.depth.CoinExCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.coinw.depth.CoinWCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.gateio.depth.GateIOCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.huobi.depth.HuobiCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.kucoin.depth.KucoinCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.lbank.depth.LBankCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.mexc.depth.MexcCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.okx.depth.OKXCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.poloniex.depth.PoloniexCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.probit.depth.ProbitCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.responsedata.coindepth.CoinDepth;
import com.exchange.scanner.dto.response.exchangedata.responsedata.coindepth.CoinDepthAsk;
import com.exchange.scanner.dto.response.exchangedata.responsedata.coindepth.CoinDepthBid;
import com.exchange.scanner.dto.response.exchangedata.xt.depth.XTCoinDepth;
import lombok.extern.slf4j.Slf4j;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.DatatypeConverter;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class ApiExchangeUtils {

    public static String generateGateIOSignature(String secret, String data) {
        String algorithm = "HmacSHA512";

        try {
            Mac sha512Hmac = Mac.getInstance(algorithm);
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), algorithm);
            sha512Hmac.init(secretKey);
            byte[] hash = sha512Hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return DatatypeConverter.printHexBinary(hash).toLowerCase();
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            log.error("Ошибка генерации подписи запроса для Gate.io. Причина: {}", ex.getLocalizedMessage());
            return "";
        }
    }

    public static String hashSHA512(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return DatatypeConverter.printHexBinary(digest).toLowerCase();
        } catch (NoSuchAlgorithmException ex) {
            log.error("Ошибка в методе hashSHA512. Причина: {}", ex.getLocalizedMessage());
            return "";
        }
    }

    public static String generateMexcSignature(Map<String, String> params, String secretKey) {
        params.put("timestamp", String.valueOf(System.currentTimeMillis()));
        params.put("recvWindow", "5000");

        String queryString = params.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(queryString.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            return hexString.toString();
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            log.error("Ошибка генерации подписи запроса для Mexc. Причина: {}", ex.getLocalizedMessage());
            return "";
        }
    }

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

    public static String generateBybitSignature(String stringToSign, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            return hexString.toString();
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            log.error("Ошибка генерации подписи запроса для Bybit. Причина: {}", ex.getLocalizedMessage());
            return "";
        }
    }

    public static CoinDepth getBinanceCoinDepth(BinanceCoinDepth depth) {

        CoinDepth coinDepth = new CoinDepth();
        coinDepth.setCoinName(depth.getCoinName());

        Set<CoinDepthBid> coinDepthBids = depth.getBids().stream().map(value -> {
            CoinDepthBid coinDepthBid = new CoinDepthBid();
            coinDepthBid.setPrice(value.get(0));
            coinDepthBid.setVolume(value.get(1));
            return coinDepthBid;
        }).collect(Collectors.toSet());

        Set<CoinDepthAsk> coinDepthAsks = depth.getAsks().stream().map(value -> {
            CoinDepthAsk coinDepthAsk = new CoinDepthAsk();
            coinDepthAsk.setPrice(value.get(0));
            coinDepthAsk.setVolume(value.get(1));
            return coinDepthAsk;
        }).collect(Collectors.toSet());

        coinDepth.setCoinDepthBids(coinDepthBids);
        coinDepth.setCoinDepthAsks(coinDepthAsks);

        return coinDepth;
    }

    public static CoinDepth getGateIOCoinDepth(GateIOCoinDepth depth) {

        CoinDepth coinDepth = new CoinDepth();
        coinDepth.setCoinName(depth.getCoinName());

        Set<CoinDepthBid> coinDepthBids = depth.getBids().stream().map(value -> {
            CoinDepthBid coinDepthBid = new CoinDepthBid();
            coinDepthBid.setPrice(value.get(0));
            coinDepthBid.setVolume(value.get(1));
            return coinDepthBid;
        }).collect(Collectors.toSet());

        Set<CoinDepthAsk> coinDepthAsks = depth.getAsks().stream().map(value -> {
            CoinDepthAsk coinDepthAsk = new CoinDepthAsk();
            coinDepthAsk.setPrice(value.get(0));
            coinDepthAsk.setVolume(value.get(1));
            return coinDepthAsk;
        }).collect(Collectors.toSet());

        coinDepth.setCoinDepthBids(coinDepthBids);
        coinDepth.setCoinDepthAsks(coinDepthAsks);

        return coinDepth;
    }

    public static CoinDepth getMexcCoinDepth(MexcCoinDepth depth) {

        CoinDepth coinDepth = new CoinDepth();
        coinDepth.setCoinName(depth.getCoinName());

        Set<CoinDepthBid> coinDepthBids = depth.getBids().stream().map(value -> {
            CoinDepthBid coinDepthBid = new CoinDepthBid();
            coinDepthBid.setPrice(value.get(0));
            coinDepthBid.setVolume(value.get(1));
            return coinDepthBid;
        }).collect(Collectors.toSet());

        Set<CoinDepthAsk> coinDepthAsks = depth.getAsks().stream().map(value -> {
            CoinDepthAsk coinDepthAsk = new CoinDepthAsk();
            coinDepthAsk.setPrice(value.get(0));
            coinDepthAsk.setVolume(value.get(1));
            return coinDepthAsk;
        }).collect(Collectors.toSet());

        coinDepth.setCoinDepthBids(coinDepthBids);
        coinDepth.setCoinDepthAsks(coinDepthAsks);

        return coinDepth;
    }

    public static CoinDepth getBybitCoinDepth(BybitCoinDepth depth) {

        CoinDepth coinDepth = new CoinDepth();
        coinDepth.setCoinName(depth.getCoinName());

        Set<CoinDepthBid> coinDepthBids = depth.getResult().getBids().stream().map(value -> {
            CoinDepthBid coinDepthBid = new CoinDepthBid();
            coinDepthBid.setPrice(value.get(0));
            coinDepthBid.setVolume(value.get(1));
            return coinDepthBid;
        }).collect(Collectors.toSet());

        Set<CoinDepthAsk> coinDepthAsks = depth.getResult().getAsks().stream().map(value -> {
            CoinDepthAsk coinDepthAsk = new CoinDepthAsk();
            coinDepthAsk.setPrice(value.get(0));
            coinDepthAsk.setVolume(value.get(1));
            return coinDepthAsk;
        }).collect(Collectors.toSet());

        coinDepth.setCoinDepthBids(coinDepthBids);
        coinDepth.setCoinDepthAsks(coinDepthAsks);

        return coinDepth;
    }

    public static CoinDepth getKucoinCoinDepth(KucoinCoinDepth depth) {

        CoinDepth coinDepth = new CoinDepth();
        coinDepth.setCoinName(depth.getCoinName());

        Set<CoinDepthBid> coinDepthBids = depth.getData().getBids().stream().map(value -> {
            CoinDepthBid coinDepthBid = new CoinDepthBid();
            coinDepthBid.setPrice(value.get(0));
            coinDepthBid.setVolume(value.get(1));
            return coinDepthBid;
        }).collect(Collectors.toSet());

        Set<CoinDepthAsk> coinDepthAsks = depth.getData().getAsks().stream().map(value -> {
            CoinDepthAsk coinDepthAsk = new CoinDepthAsk();
            coinDepthAsk.setPrice(value.get(0));
            coinDepthAsk.setVolume(value.get(1));
            return coinDepthAsk;
        }).collect(Collectors.toSet());

        coinDepth.setCoinDepthBids(coinDepthBids);
        coinDepth.setCoinDepthAsks(coinDepthAsks);

        return coinDepth;
    }

    public static CoinDepth getBitgetCoinDepth(BitgetCoinDepth depth) {

        CoinDepth coinDepth = new CoinDepth();
        coinDepth.setCoinName(depth.getCoinName());

        Set<CoinDepthAsk> coinDepthAsks = depth.getData().getAsks().stream().map(value -> {
            CoinDepthAsk coinDepthAsk = new CoinDepthAsk();
            coinDepthAsk.setPrice(value.get(0));
            coinDepthAsk.setVolume(value.get(1));
            return coinDepthAsk;
        }).collect(Collectors.toSet());

        Set<CoinDepthBid> coinDepthBids = depth.getData().getBids().stream().map(value -> {
            CoinDepthBid coinDepthBid = new CoinDepthBid();
            coinDepthBid.setPrice(value.get(0));
            coinDepthBid.setVolume(value.get(1));
            return coinDepthBid;
        }).collect(Collectors.toSet());

        coinDepth.setCoinDepthBids(coinDepthBids);
        coinDepth.setCoinDepthAsks(coinDepthAsks);

        return coinDepth;
    }

    public static CoinDepth getHuobiCoinDepth(HuobiCoinDepth depth) {
        CoinDepth coinDepth = new CoinDepth();
        coinDepth.setCoinName(depth.getCoinName());

        Set<CoinDepthBid> coinDepthBids = depth.getTick().getBids().stream().map(value -> {
            CoinDepthBid coinDepthBid = new CoinDepthBid();
            coinDepthBid.setPrice(value.get(0));
            coinDepthBid.setVolume(value.get(1));
            return coinDepthBid;
        }).collect(Collectors.toSet());

        Set<CoinDepthAsk> coinDepthAsks = depth.getTick().getAsks().stream().map(value -> {
            CoinDepthAsk coinDepthAsk = new CoinDepthAsk();
            coinDepthAsk.setPrice(value.get(0));
            coinDepthAsk.setVolume(value.get(1));
            return coinDepthAsk;
        }).collect(Collectors.toSet());

        coinDepth.setCoinDepthBids(coinDepthBids);
        coinDepth.setCoinDepthAsks(coinDepthAsks);

        return coinDepth;
    }

    public static CoinDepth getPoloniexCoinDepth(PoloniexCoinDepth depth) {

        CoinDepth coinDepth = new CoinDepth();
        coinDepth.setCoinName(depth.getCoinName());

        Set<CoinDepthBid> coinDepthBids = new HashSet<>();

        int checkBidOrdersSum = 0;
        for (int i = 0; i < depth.getBids().size(); i++) {
            CoinDepthBid coinDepthBid = new CoinDepthBid();
            coinDepthBid.setPrice(depth.getBids().get(i));
            coinDepthBid.setVolume(depth.getBids().get(i + 1));
            checkBidOrdersSum += 2;
            coinDepthBids.add(coinDepthBid);
            if (checkBidOrdersSum == depth.getBids().size()) break;
        }

        Set<CoinDepthAsk> coinDepthAsks =new HashSet<>();

        int checkAskOrdersSum = 0;
        for (int i = 0; i < depth.getAsks().size(); i++) {
            CoinDepthAsk coinDepthAsk = new CoinDepthAsk();
            coinDepthAsk.setPrice(depth.getAsks().get(i));
            coinDepthAsk.setVolume(depth.getAsks().get(i + 1));
            checkAskOrdersSum += 2;
            coinDepthAsks.add(coinDepthAsk);
            if (checkAskOrdersSum == depth.getAsks().size()) break;
        }

        coinDepth.setCoinDepthBids(coinDepthBids);
        coinDepth.setCoinDepthAsks(coinDepthAsks);

        return coinDepth;
    }

    public static CoinDepth getOKXCoinDepth(OKXCoinDepth depth) {
        CoinDepth coinDepth = new CoinDepth();
        coinDepth.setCoinName(depth.getCoinName());

        Set<CoinDepthBid> coinDepthBids = depth.getData().getFirst().getBids().stream().map(value -> {
            CoinDepthBid coinDepthBid = new CoinDepthBid();
            coinDepthBid.setPrice(value.get(0));
            coinDepthBid.setVolume(value.get(1));
            return coinDepthBid;
        }).collect(Collectors.toSet());

        Set<CoinDepthAsk> coinDepthAsks = depth.getData().getFirst().getAsks().stream().map(value -> {
            CoinDepthAsk coinDepthAsk = new CoinDepthAsk();
            coinDepthAsk.setPrice(value.get(0));
            coinDepthAsk.setVolume(value.get(1));
            return coinDepthAsk;
        }).collect(Collectors.toSet());

        coinDepth.setCoinDepthBids(coinDepthBids);
        coinDepth.setCoinDepthAsks(coinDepthAsks);

        return coinDepth;
    }

    public static CoinDepth getBitmartCoinDepth(BitmartCoinDepth depth) {

        CoinDepth coinDepth = new CoinDepth();
        String coinName = depth.getData().getSymbol().replaceAll("_USDT", "");
        coinDepth.setCoinName(coinName);

        Set<CoinDepthBid> coinDepthBids = depth.getData().getBids().stream().map(value -> {
            CoinDepthBid coinDepthBid = new CoinDepthBid();
            coinDepthBid.setPrice(value.get(0));
            coinDepthBid.setVolume(value.get(1));
            return coinDepthBid;
        }).collect(Collectors.toSet());

        Set<CoinDepthAsk> coinDepthAsks = depth.getData().getAsks().stream().map(value -> {
            CoinDepthAsk coinDepthAsk = new CoinDepthAsk();
            coinDepthAsk.setPrice(value.get(0));
            coinDepthAsk.setVolume(value.get(1));
            return coinDepthAsk;
        }).collect(Collectors.toSet());

        coinDepth.setCoinDepthBids(coinDepthBids);
        coinDepth.setCoinDepthAsks(coinDepthAsks);

        return coinDepth;
    }

    public static CoinDepth getLBankCoinDepth(LBankCoinDepth depth) {

        CoinDepth coinDepth = new CoinDepth();
        coinDepth.setCoinName(depth.getCoinName());

        Set<CoinDepthBid> coinDepthBids = depth.getData().getBids().stream().map(value -> {
            CoinDepthBid coinDepthBid = new CoinDepthBid();
            coinDepthBid.setPrice(value.get(0));
            coinDepthBid.setVolume(value.get(1));
            return coinDepthBid;
        }).collect(Collectors.toSet());

        Set<CoinDepthAsk> coinDepthAsks = depth.getData().getAsks().stream().map(value -> {
            CoinDepthAsk coinDepthAsk = new CoinDepthAsk();
            coinDepthAsk.setPrice(value.get(0));
            coinDepthAsk.setVolume(value.get(1));
            return coinDepthAsk;
        }).collect(Collectors.toSet());

        coinDepth.setCoinDepthBids(coinDepthBids);
        coinDepth.setCoinDepthAsks(coinDepthAsks);

        return coinDepth;
    }

    public static CoinDepth getCoinExCoinDepth(CoinExCoinDepth depth) {

        CoinDepth coinDepth = new CoinDepth();
        String coinName = depth.getData().getMarket().replaceAll("USDT", "");
        coinDepth.setCoinName(coinName);

        Set<CoinDepthBid> coinDepthBids = depth.getData().getDepth().getBids().stream().map(value -> {
            CoinDepthBid coinDepthBid = new CoinDepthBid();
            coinDepthBid.setPrice(value.get(0));
            coinDepthBid.setVolume(value.get(1));
            return coinDepthBid;
        }).collect(Collectors.toSet());

        Set<CoinDepthAsk> coinDepthAsks = depth.getData().getDepth().getAsks().stream().map(value -> {
            CoinDepthAsk coinDepthAsk = new CoinDepthAsk();
            coinDepthAsk.setPrice(value.get(0));
            coinDepthAsk.setVolume(value.get(1));
            return coinDepthAsk;
        }).collect(Collectors.toSet());

        coinDepth.setCoinDepthBids(coinDepthBids);
        coinDepth.setCoinDepthAsks(coinDepthAsks);

        return coinDepth;
    }

    public static CoinDepth getCoinWCoinDepth(CoinWCoinDepth depth) {

        CoinDepth coinDepth = new CoinDepth();
        coinDepth.setCoinName(depth.getCoinName());

        Set<CoinDepthBid> coinDepthBids = depth.getData().getBids().stream().map(value -> {
            CoinDepthBid coinDepthBid = new CoinDepthBid();
            coinDepthBid.setPrice(value.getPrice());
            coinDepthBid.setVolume(value.getAmount());
            return coinDepthBid;
        }).collect(Collectors.toSet());

        Set<CoinDepthAsk> coinDepthAsks = depth.getData().getAsks().stream().map(value -> {
            CoinDepthAsk coinDepthAsk = new CoinDepthAsk();
            coinDepthAsk.setPrice(value.getPrice());
            coinDepthAsk.setVolume(value.getAmount());
            return coinDepthAsk;
        }).collect(Collectors.toSet());

        coinDepth.setCoinDepthBids(coinDepthBids);
        coinDepth.setCoinDepthAsks(coinDepthAsks);

        return coinDepth;
    }

    public static CoinDepth getXTCoinDepth(XTCoinDepth depth) {
        CoinDepth coinDepth = new CoinDepth();
        coinDepth.setCoinName(depth.getCoinName());

        Set<CoinDepthBid> coinDepthBids = depth.getResult().getBids().stream().map(value -> {
            CoinDepthBid coinDepthBid = new CoinDepthBid();
            coinDepthBid.setPrice(value.get(0));
            coinDepthBid.setVolume(value.get(1));
            return coinDepthBid;
        }).collect(Collectors.toSet());

        Set<CoinDepthAsk> coinDepthAsks = depth.getResult().getAsks().stream().map(value -> {
            CoinDepthAsk coinDepthAsk = new CoinDepthAsk();
            coinDepthAsk.setPrice(value.get(0));
            coinDepthAsk.setVolume(value.get(1));
            return coinDepthAsk;
        }).collect(Collectors.toSet());

        coinDepth.setCoinDepthBids(coinDepthBids);
        coinDepth.setCoinDepthAsks(coinDepthAsks);

        return coinDepth;
    }

    public static CoinDepth getProbitCoinDepth(ProbitCoinDepth depth) {

        CoinDepth coinDepth = new CoinDepth();
        coinDepth.setCoinName(depth.getCoinName());

        Set<CoinDepthBid> coinDepthBids = depth.getData().stream().filter(data -> data.getSide().equals("sell")).map(value -> {
            CoinDepthBid coinDepthBid = new CoinDepthBid();
            coinDepthBid.setPrice(value.getPrice());
            coinDepthBid.setVolume(value.getQuantity());
            return coinDepthBid;
        }).collect(Collectors.toSet());

        Set<CoinDepthAsk> coinDepthAsks = depth.getData().stream().filter(data -> data.getSide().equals("buy")).map(value -> {
            CoinDepthAsk coinDepthAsk = new CoinDepthAsk();
            coinDepthAsk.setPrice(value.getPrice());
            coinDepthAsk.setVolume(value.getQuantity());
            return coinDepthAsk;
        }).collect(Collectors.toSet());

        coinDepth.setCoinDepthBids(coinDepthBids);
        coinDepth.setCoinDepthAsks(coinDepthAsks);

        return coinDepth;
    }

    public static CoinDepth getBingXCoinDepth(BingXCoinDepth depth) {

        CoinDepth coinDepth = new CoinDepth();
        coinDepth.setCoinName(depth.getCoinName());

        Set<CoinDepthBid> coinDepthBids = depth.getData().getBids().stream().map(value -> {
            CoinDepthBid coinDepthBid = new CoinDepthBid();
            coinDepthBid.setPrice(value.get(0));
            coinDepthBid.setVolume(value.get(1));
            return coinDepthBid;
        }).collect(Collectors.toSet());

        Set<CoinDepthAsk> coinDepthAsks = depth.getData().getAsks().stream().map(value -> {
            CoinDepthAsk coinDepthAsk = new CoinDepthAsk();
            coinDepthAsk.setPrice(value.get(0));
            coinDepthAsk.setVolume(value.get(1));
            return coinDepthAsk;
        }).collect(Collectors.toSet());

        coinDepth.setCoinDepthBids(coinDepthBids);
        coinDepth.setCoinDepthAsks(coinDepthAsks);

        return coinDepth;
    }

    public static String getCoinWSymbolNumber(String responseRaw, String coinName) {

        int coinNameIndex = responseRaw.indexOf(coinName);
        int startIndex = responseRaw.lastIndexOf(" ", coinNameIndex) + 1;

        return responseRaw.substring(startIndex, coinNameIndex - 1);
    }
}
