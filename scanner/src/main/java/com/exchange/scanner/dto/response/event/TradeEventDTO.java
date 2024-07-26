package com.exchange.scanner.dto.response.event;

import com.exchange.scanner.model.Ask;
import com.exchange.scanner.model.Bid;
import com.exchange.scanner.model.Chain;

import java.math.BigDecimal;
import java.util.Set;
import java.util.TreeSet;

public interface TradeEventDTO {
    void setExchange(String name);

    void setCoin(String name);

    void setDepositLink(String depositLink);

    void setWithdrawLink(String withdrawLink);

    void setTradeLink(String tradeLink);

    void setCoinMarketCapLink(String coinMarketCapLink);

    void setLogoLink(String logoLink);

    void setTakerFee(BigDecimal takerFee);

    void setVolume24h(BigDecimal volume24h);

    void setAsks(TreeSet<Ask> asks);

    void setBids(TreeSet<Bid> bids);

    void setMinUserTradeAmount(BigDecimal minAmount);

    void setMaxUserTradeAmount(BigDecimal maxAmount);

    void setUserMinProfit(BigDecimal minProfit);

    void setChains(Set<Chain> chains);

    void setMostProfitableChain(Chain mostProfitableChain);

    void setTotalTradeVolume(BigDecimal totalTradeVolume);

    void setIsMargin(Boolean isMargin);
}
