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
public class UserSellTradeEventDTO implements TradeEventDTO, Comparable<UserSellTradeEventDTO>, Cloneable {

    private String exchange;

    private String coin;

    private String depositLink;

    private String withdrawLink;

    private String tradeLink;

    private Boolean hasQueryCoinLinkParam;

    private String coinMarketCapLink;

    private String logoLink;

    private BigDecimal takerFee;

    private BigDecimal volume24h;

    private BigDecimal minUserTradeAmount;

    private BigDecimal maxUserTradeAmount;

    private BigDecimal userMinProfit;

    private Set<Chain> chains;

    private Chain mostProfitableChain;

    private BigDecimal totalTradeVolume;

    private TreeSet<Bid> bids;

    private TreeSet<Ask> asks;

    private Integer confirmations;

    private Boolean isMargin;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserSellTradeEventDTO that = (UserSellTradeEventDTO) o;
        return Objects.equals(exchange, that.exchange) && Objects.equals(coin, that.coin);
    }

    @Override
    public int hashCode() {
        return Objects.hash(exchange, coin);
    }

    @Override
    public void setAsks(TreeSet<Ask> asks) {
        this.asks = null;
    }

    @Override
    public int compareTo(UserSellTradeEventDTO o) {
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
    public UserSellTradeEventDTO clone() {
        try {
            UserSellTradeEventDTO clone = (UserSellTradeEventDTO) super.clone();
            clone.asks = new TreeSet<>();
            clone.bids = new TreeSet<>();
            clone.mostProfitableChain = null;
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }
}
