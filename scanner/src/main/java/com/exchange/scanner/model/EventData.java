package com.exchange.scanner.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.Objects;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long id;

    @Column(name = "exhcange_for_buy", nullable = false)
    private String exchangeForBuy;

    @Column(name = "exchange_for_sell", nullable = false)
    private String exchangeForSell;

    @Column(name = "deposit_link", nullable = false)
    private String depositLink;

    @Column(name = "withdraw_link", nullable = false)
    private String withdrawLink;

    @Column(name = "buy_trading_link", nullable = false)
    private String buyTradingLink;

    @Column(name = "sell_trading_link", nullable = false)
    private String sellTradingLink;

    @Column(name = "fiat_volume", nullable = false)
    private String fiatVolume;

    @Column(name = "coin_volume", nullable = false)
    private String coinVolume;

    @Column(name = "fiat_spread", nullable = false)
    private String fiatSpread;

    @Column(name = "average_price_for_buy", nullable = false)
    private String averagePriceForBuy;

    @Column(name = "average_price_for_sell", nullable = false)
    private String averagePriceForSell;

    @Column(name = "price_range_for_buy", nullable = false)
    private String priceRangeForBuy;

    @Column(name = "price_range_for_sell", nullable = false)
    private String priceRangeForSell;

    @Column(name = "volume24_exchange_for_buy", nullable = false)
    private String volume24ExchangeForBuy;

    @Column(name = "volume24_exchange_for_sell", nullable = false)
    private String volume24ExchangeForSell;

    @Column(name = "orders_count_for_buy", nullable = false)
    private String ordersCountForBuy;

    @Column(name = "orders_count_for_sell", nullable = false)
    private String ordersCountForSell;

    @Column(name = "spot_fee", nullable = false)
    private String spotFee;

    @Column(name = "chain_fee", nullable = false)
    private String chainFee;

    @Column(name = "chain_name", nullable = false)
    private String chainName;

    @Column(name = "transaction_time", nullable = false)
    private String transactionTime = "null";

    @Column(name = "transaction_confirmation", nullable = false)
    private String transactionConfirmation = "null";

    @Column(nullable = false)
    private Boolean margin;

    @Column(nullable = false)
    private String slug;

    @Column(name = "is_warning", nullable = false)
    private Boolean isWarning;

    @Column(nullable = false)
    private Long timestamp;

    @ManyToOne
    @JoinColumn(name = "arbitrage_event_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ArbitrageEvent arbitrageEvent;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EventData eventData = (EventData) o;
        return Objects.equals(slug, eventData.slug);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(slug);
    }
}
