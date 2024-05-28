package com.exchange.scanner.services.impl.api.utils;

import com.exchange.scanner.model.Coin;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CoinFactory {

    public static Coin getCoin(String coinName) {
        Coin coin = new Coin();
        coin.setName(coinName);
        coin.setSymbol(coinName);
        return coin;
    }

    public static String refactorToStandardCoinName(String initialCoinName, String patternToMatch) {
        String coinName = "";
        Pattern pattern = Pattern.compile(".*(?=" + Pattern.quote(patternToMatch) + ")");
        Matcher matcher = pattern.matcher(initialCoinName);

        if (matcher.find()) {
            coinName = matcher.group().toUpperCase();
        }
        return coinName;
    }
}
