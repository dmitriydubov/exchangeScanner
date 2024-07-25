package com.exchange.scanner.services.utils.AppUtils;

import com.exchange.scanner.dto.response.ChainResponseDTO;
import com.exchange.scanner.dto.response.TradingFeeResponseDTO;
import com.exchange.scanner.dto.response.Volume24HResponseDTO;
import com.exchange.scanner.dto.response.LinkDTO;
import com.exchange.scanner.model.Chain;
import com.exchange.scanner.model.Coin;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ObjectUtils {

    public static Coin getCoin(String coinName, String exchangeName, LinkDTO links) {
        Coin coin = new Coin();
        coin.setName(coinName);
        coin.setSlug(coinName + "-" + exchangeName);
        coin.setDepositLink(links.getDepositLink());
        coin.setWithdrawLink(links.getWithdrawLink());
        coin.setTradeLink(links.getTradeLink());
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

    public static ChainResponseDTO getChainResponseDTO(String exchangeName, Coin coin, Set<Chain> chains) {
        ChainResponseDTO responseDTO = new ChainResponseDTO();
        responseDTO.setExchange(exchangeName);
        responseDTO.setCoin(coin);
        responseDTO.setChains(chains);
        return responseDTO;
    }

    public static TradingFeeResponseDTO getTradingFeeResponseDTO(String exchangeName, Coin coin, String commission) {
        TradingFeeResponseDTO responseDTO = new TradingFeeResponseDTO();
        responseDTO.setExchange(exchangeName);
        responseDTO.setCoin(coin);
        responseDTO.setTradingFee(new BigDecimal(commission).setScale(4, RoundingMode.CEILING));
        return responseDTO;
    }

    public static Volume24HResponseDTO getVolume24HResponseDTO(String exchange, Coin coin, String volume24H) {
        Volume24HResponseDTO responseDTO = new Volume24HResponseDTO();
        responseDTO.setExchange(exchange);
        responseDTO.setCoin(coin);
        responseDTO.setVolume24H(new BigDecimal(volume24H).setScale(4, RoundingMode.CEILING));
        return responseDTO;
    }
}
