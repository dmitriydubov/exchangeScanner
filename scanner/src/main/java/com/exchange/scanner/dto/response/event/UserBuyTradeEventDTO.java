package com.exchange.scanner.dto.response.event;

import com.exchange.scanner.model.Ask;
import com.exchange.scanner.model.Bid;
import com.exchange.scanner.model.Chain;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

@Getter
@Setter
public class UserBuyTradeEventDTO implements TradeEventDTO, Comparable<UserBuyTradeEventDTO>, Cloneable {

    private String exchange;

    private String coin;

    private String depositLink;

    private String withdrawLink;

    private String tradeLink;

    private String coinMarketCapLink;

    private String logoLink;

    private BigDecimal takerFee;

    private BigDecimal volume24h;

    private BigDecimal minUserTradeAmount;

    private BigDecimal maxUserTradeAmount;

    private BigDecimal userMinProfit;

    private Set<Chain> chains;

    private Chain mostProfitableChain;

    private TreeSet<Ask> asks;

    private TreeSet<Bid> bids;

    private BigDecimal totalTradeVolume;

    private Boolean isMargin;

    @Override
    public void setBids(TreeSet<Bid> bids) {
        this.bids = null;
    }

    @Override
    public int compareTo(UserBuyTradeEventDTO o) {
        int exchangeCompare = this.exchange.compareTo(o.exchange);
        int coinCompare = this.coin.compareTo(o.coin);
        if (exchangeCompare > 0 && coinCompare > 0) {
            return 1;
        }
        if (exchangeCompare == 0 && coinCompare == 0) {
            return 0;
        }

        return -1;
    }

    @Override
    public UserBuyTradeEventDTO clone() {
        try {
            UserBuyTradeEventDTO clone = (UserBuyTradeEventDTO) super.clone();
            clone.asks = new TreeSet<>();
            clone.bids = new TreeSet<>();
            clone.mostProfitableChain = null;
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }
}
