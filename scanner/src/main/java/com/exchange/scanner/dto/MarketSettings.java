package com.exchange.scanner.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class MarketSettings {
    @JsonProperty("markets_buy")
    private List<String> marketsBuy;

    @JsonProperty("markets_sell")
    private List<String> marketsSell;

    @JsonProperty("volume_min")
    private String volumeMin;

    @JsonProperty("volume_max")
    private String volumeMax;

    @JsonProperty("percent_spread")
    private String percentSpread;

    @JsonProperty("profit_spread")
    private String profitSpread;

    private String fee;
    private String monitoring;

    @JsonProperty("risk_type")
    private String riskType;

    @JsonProperty("hedge_type")
    private String hedgeType;

    @JsonProperty("update_time")
    private String updateTime;

    @JsonProperty("withdraw_type")
    private String withdrawType;

    private List<String> chains;
    private List<String> coins;

    @JsonProperty("white_list")
    private List<String> whiteList;

    @JsonProperty("black_list_combos")
    private List<String> blackListCombos;

    @JsonProperty("withdraw_monitoring")
    private String withdrawMonitoring;

    @JsonProperty("deposit_monitoring")
    private String depositMonitoring;

    @JsonProperty("listing_monitoring")
    private String listingMonitoring;

    @JsonProperty("bw_list_type")
    private String bwListType;

    @JsonProperty("pinned_coins")
    private List<String> pinnedCoins;
}
