package com.exchange.scanner.services.utils.AppUtils;

import com.exchange.scanner.dto.response.ChainResponseDTO;
import com.exchange.scanner.model.Coin;
import com.exchange.scanner.model.Exchange;
import com.exchange.scanner.repositories.ExchangeRepository;
import com.exchange.scanner.repositories.UserMarketSettingsRepository;
import com.exchange.scanner.services.ApiExchangeAdapter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
public class CoinChainUtils {

    public Set<ChainResponseDTO> getCoinsChainInfoAsync(ApiExchangeAdapter apiExchangeAdapter,
                                                         ExchangeRepository exchangeRepository,
                                                         UserMarketSettingsRepository userMarketSettingsRepository
    ) {
        Set<ChainResponseDTO> result = Collections.synchronizedSet(new HashSet<>());
        Set<Exchange> exchanges = new HashSet<>(exchangeRepository.findAll());
        ExecutorService executorService = Executors.newFixedThreadPool(exchanges.size());
        List<CompletableFuture<Void>> futures = new CopyOnWriteArrayList<>();

        exchanges.forEach(exchange -> {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                Set<Coin> coins = exchange.getCoins();
                Set<ChainResponseDTO> chainResponseDTOSet = apiExchangeAdapter.getCoinChain(exchange.getName(), coins);
                synchronized (result) {
                    result.addAll(chainResponseDTOSet);
                }
            }, executorService);
            futures.add(future);
        });

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executorService.shutdown();

        return result;
    }

    public static String unifyChainName(String initialChainName) {
        String finalChainName = initialChainName;

        //Bitcoin chain
        if (initialChainName.equalsIgnoreCase("BRC20") ||
                initialChainName.equalsIgnoreCase("BTCK") ||
                initialChainName.equalsIgnoreCase("BTC_BRC20") ||
                initialChainName.equalsIgnoreCase("Bitcoin")
        ) {
            finalChainName = "BTC";
        }

        //TON chain
        if (initialChainName.equalsIgnoreCase("TONCOIN")) {
            finalChainName = "TON";
        }

        //Matic chain
        if (initialChainName.equalsIgnoreCase("PRC20") ||
                initialChainName.equalsIgnoreCase("POLYGON") ||
                initialChainName.equalsIgnoreCase("MATICPOLY") ||
                initialChainName.equalsIgnoreCase("MATIC6S")
        ) {
            finalChainName = "MATIC";
        }

        //Binance smart chain
        if (initialChainName.equalsIgnoreCase("BEP20ETH") ||
                initialChainName.equalsIgnoreCase("BEP20SIS") ||
                initialChainName.equalsIgnoreCase("BEP20") ||
                initialChainName.equalsIgnoreCase("BEP2") ||
                initialChainName.equalsIgnoreCase("NFP") ||
                initialChainName.equalsIgnoreCase("BEP20(BSC)") ||
                initialChainName.equalsIgnoreCase("BNB SMART CHAIN") ||
                initialChainName.equalsIgnoreCase("BNB") ||
                initialChainName.equalsIgnoreCase("OPBNB")
        ) {
            finalChainName = "BSC";
        }

        //TRON chain
        if (initialChainName.equalsIgnoreCase("TRC20") ||
                initialChainName.equalsIgnoreCase("TRON")
        ) {
            finalChainName = "TRX";
        }

        //Solana chain
        if (initialChainName.equalsIgnoreCase("SOL") ||
                initialChainName.equalsIgnoreCase("SPL") ||
                initialChainName.equalsIgnoreCase("SOL-SOL")
        ) {
            finalChainName = "SOLANA";
        }

        //Ethereum chain
        if (initialChainName.equalsIgnoreCase("ERC20") ||
                initialChainName.equalsIgnoreCase("RING") ||
                initialChainName.equalsIgnoreCase("WGP") ||
                initialChainName.equalsIgnoreCase("OPUL") ||
                initialChainName.equalsIgnoreCase("ERC404") ||
                initialChainName.equalsIgnoreCase("SIS") ||
                initialChainName.equalsIgnoreCase("ERC20TPY") ||
                initialChainName.equalsIgnoreCase("ZKP") ||
                initialChainName.equalsIgnoreCase("ETHW") ||
                initialChainName.equalsIgnoreCase("ETHK") ||
                initialChainName.equalsIgnoreCase("ZEN20") ||
                initialChainName.equalsIgnoreCase("ERC20@SOL") ||
                initialChainName.equalsIgnoreCase("ERC20@TRC20") ||
                initialChainName.equalsIgnoreCase("ERC20@POLYGON@SOL@FTM") ||
                initialChainName.equalsIgnoreCase("ERC20@OP") ||
                initialChainName.equalsIgnoreCase("ERC20@POLYGON") ||
                initialChainName.equalsIgnoreCase("ETH@Arbitrum@BASE") ||
                initialChainName.equalsIgnoreCase("ETHEREUM")

        ) {
            finalChainName = "ETH";
        }

        //Litecoin chain
        if (initialChainName.equalsIgnoreCase("LTCK")) {
            finalChainName = "LTC";
        }

        //Arbitrum chain
        if (initialChainName.equalsIgnoreCase("ARBITRUMONE") ||
                initialChainName.equalsIgnoreCase("ARBITRUM ONE") ||
                initialChainName.equalsIgnoreCase("ARBITRUM") ||
                initialChainName.equalsIgnoreCase("ARC20RING") ||
                initialChainName.equalsIgnoreCase("ARC20OPUL") ||
                initialChainName.equalsIgnoreCase("ARC20SIS") ||
                initialChainName.equalsIgnoreCase("ARC20") ||
                initialChainName.equalsIgnoreCase("ARBEVM") ||
                initialChainName.equalsIgnoreCase("ARBNOVA") ||
                initialChainName.equalsIgnoreCase("ARBI") ||
                initialChainName.equalsIgnoreCase("ETHARB") ||
                initialChainName.equalsIgnoreCase("ARBITRUM_NOVA")
        ) {
            finalChainName = "ARB";
        }

        //Lightning chain
        if (initialChainName.equalsIgnoreCase("Lightning Network") ||
                initialChainName.equalsIgnoreCase("BTC-Lightning")
        ) {
            finalChainName = "LIGHTNING";
        }

        //Chiliz chain
        if (initialChainName.equalsIgnoreCase("CAP20") ||
                initialChainName.equalsIgnoreCase("CHILIZ") ||
                initialChainName.equalsIgnoreCase("CHILIZ CHAIN") ||
                initialChainName.equalsIgnoreCase("CHZ2")
        ) {
            finalChainName = "CHZ";
        }

        //Avax chain
        if (initialChainName.equalsIgnoreCase("AVAX CCHAIN") ||
                initialChainName.equalsIgnoreCase("AVAX_CCHAIN") ||
                initialChainName.equalsIgnoreCase("AVAX-C") ||
                initialChainName.equalsIgnoreCase("AVAX-CCHAIN") ||
                initialChainName.equalsIgnoreCase("AVA_C") ||
                initialChainName.equalsIgnoreCase("AVAX-C@Arbitrum") ||
                initialChainName.equalsIgnoreCase("AVAX-X@AVAX-C") ||
                initialChainName.equalsIgnoreCase("AVAX C-Chain")
        ) {
            finalChainName = "AVAX";
        }

        //Base chain
        if (initialChainName.equalsIgnoreCase("ETHBASE") ||
                initialChainName.equalsIgnoreCase("BASE MAINNET")
        ) {
            finalChainName = "BASE";
        }

        //ZKSYNC chain
        if (initialChainName.equalsIgnoreCase("ETHZKSYNC") ||
                initialChainName.equalsIgnoreCase("ZKSYNCERA_ETH") ||
                initialChainName.equalsIgnoreCase("ZkSync ERA") ||
                initialChainName.equalsIgnoreCase("ZKSYNCERA")
        ) {
            finalChainName = "ZKSYNC";
        }

        //Linea chain
        if (initialChainName.equalsIgnoreCase("ETHLINEA") ||
                initialChainName.equalsIgnoreCase("LINEA-ETH")
        ) {
            finalChainName = "LINEA";
        }

        //WBTC chain
        if (initialChainName.equalsIgnoreCase("WBTCK")) {
            finalChainName = "WBTC";
        }

        //SHIB chain
        if (initialChainName.equalsIgnoreCase("SHIBK")) {
            finalChainName = "SHIB";
        }

        //Link chain
        if (initialChainName.equalsIgnoreCase("LINKK")) {
            finalChainName = "LINK";
        }

        //Sushi chain
        if (initialChainName.equalsIgnoreCase("SUSHIK") ||
                initialChainName.equalsIgnoreCase("SUSHI4S")
        ) {
            finalChainName = "SUSHI";
        }

        //DAI chain
        if (initialChainName.equalsIgnoreCase("DAIK")) {
            finalChainName = "DAI";
        }

        //UNI chain
        if (initialChainName.equalsIgnoreCase("UNIK")
        ) {
            finalChainName = "UNI";
        }

        //DOT chain
        if (initialChainName.equalsIgnoreCase("DOTK")) {
            finalChainName = "DOT";
        }

        //OMAX chain
        if (initialChainName.equalsIgnoreCase("OMAX CHAIN") ||
                initialChainName.equalsIgnoreCase("OMAX-OMAX CHAIN")
        ) {
            finalChainName = "OMAX";
        }

        //COSS chain
        if (initialChainName.equalsIgnoreCase("COSS CHAIN")) {
            finalChainName = "COSS";
        }

        //PARTISIA chain
        if (initialChainName.equalsIgnoreCase("PARTISIA CHAIN")) {
            finalChainName = "PARTISIA";
        }

        //JTC chain
        if (initialChainName.equalsIgnoreCase("JTC NETWORK")) {
            finalChainName = "JTC";
        }

        //DIS chain
        if (initialChainName.equalsIgnoreCase("DIS CHAIN")) {
            finalChainName = "DIS";
        }

        //MANTA chain
        if (initialChainName.equalsIgnoreCase("MANTA NETWORK")) {
            finalChainName = "MANTA";
        }

        //DOGE chain
        if (initialChainName.equalsIgnoreCase("DOGE3S")) {
            finalChainName = "DOGE";
        }

        //Omega chain
        if (initialChainName.equalsIgnoreCase("Omega Main Network")) {
            finalChainName = "OMEGA";
        }

        //GMMT chain
        if (initialChainName.equalsIgnoreCase("GMMT chain")) {
            finalChainName = "GMMT";
        }

        //Monero chain
        if (initialChainName.equalsIgnoreCase("MONERO")) {
            finalChainName = "XMR";
        }

        //RwaChain
        if (initialChainName.equalsIgnoreCase("RwaChain Mainnet")) {
            finalChainName = "RwaChain";
        }

        //Ripple chain
        if (initialChainName.equalsIgnoreCase("Ripple")) {
            finalChainName = "XRP";
        }

        //XLM chain
        if (initialChainName.equalsIgnoreCase("Stellar Network")) {
            finalChainName = "XLM";
        }

        //NEAR chain
        if (initialChainName.equalsIgnoreCase("NEAR Protocol")) {
            finalChainName = "NEAR";
        }

        //ADA network
        if (initialChainName.equalsIgnoreCase("Cardano")) {
            finalChainName = "ADA";
        }

        //KAVA chain
        if (initialChainName.equalsIgnoreCase("KAVAEVM")) {
            finalChainName = "KAVA";
        }

        //NEO chain
        if (initialChainName.equalsIgnoreCase("NEO3")) {
            finalChainName = "NEO";
        }

        return finalChainName;
    }
}
