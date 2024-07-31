import React from 'react';
import Select from 'react-select';
import { request } from './axios_helper';
import currencyLogo from '../currency-bitcoin.svg'
import withdrawLogo from '../withdraw.svg'
import depositLogo from '../deposit.svg'

export default class Dashboard extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      data: [],
      deals: [],
      expandedCoin: null,
      selectedBuyExchanges: [],
      selectedSellExchanges: [],
      availableCoins: [],
      selectedCoins: [],
      minProfit: '',
      minDealAmount: '',
      maxDealAmount: ''
    };
    this.intervalId = null;
  }

  fetchArbitrageEvents = () => {
    request("GET", "/api/v1/app/refresh-data", {}).then(response => {
      this.setState({ deals: response.data });
    });
  }

  componentDidMount() {
    request("GET", "/api/v1/app/get-exchanges", {}).then(response => {
      this.setState({
        data: response.data,
        availableCoins: Array.from(response.data.coins),
        selectedBuyExchanges: Array.from(response.data.userMarketsBuy),
        selectedSellExchanges: Array.from(response.data.userMarketsSell),
        selectedCoins: Array.from(response.data.userCoinsNames),
        minProfit: response.data.minUserProfit,
        minDealAmount: response.data.minUserVolume,
        maxDealAmount: response.data.maxUserVolume
      });
      this.fetchArbitrageEvents();
    });

    this.intervalId = setInterval(this.fetchArbitrageEvents, 5000);
  }

  componentWillUnmount() {
    clearInterval(this.intervalId);
  }

  handleExchangeToggle = (exchange, type) => {
    this.setState(prevState => {
      const selectedExchanges = type === 'buy' ? prevState.selectedBuyExchanges : prevState.selectedSellExchanges;
      const isSelected = selectedExchanges.includes(exchange);
      const updatedExchanges = isSelected
        ? selectedExchanges.filter(item => item !== exchange)
        : [...selectedExchanges, exchange];

      return type === 'buy'
        ? { selectedBuyExchanges: updatedExchanges }
        : { selectedSellExchanges: updatedExchanges };
    });
  }

  handleCoinSelect = selectedOptions => {
    this.setState({ selectedCoins: selectedOptions.map(option => option.value) });
  }

  handleCoinRemove = coin => {
    this.setState(prevState => ({
      selectedCoins: prevState.selectedCoins.filter(item => item !== coin)
    }));
  }

  handleInputChange = event => {
    const { name, value } = event.target;
    this.setState({ [name]: value });
  }

  handleFilterSubmit = () => {
    const { selectedBuyExchanges, selectedSellExchanges, selectedCoins, minProfit, minDealAmount, maxDealAmount } = this.state;

    request(
        "POST", "/api/v1/app/update", 
        { 
            buyExchanges: selectedBuyExchanges,
            sellExchanges: selectedSellExchanges,
            coins: selectedCoins,
            minProfit: minProfit,
            minDealAmount: minDealAmount,
            maxDealAmount: maxDealAmount 
        }
    ).then(response => {
        this.setState({ data: response.data });
}   );
  }

  toggleExpand = (coin, event) => {
    event.stopPropagation();
    this.setState(prevState => ({
      expandedCoin: prevState.expandedCoin === coin ? null : coin
    }));
  }

  render() {
    const coinOptions = this.state.availableCoins.map(coin => ({ value: coin, label: coin }));

    return (
      <div className="container">
        <div className="row justify-content-md-center">
          <div className="col-8">
            <div className="form-group mt-2">
              <label className="d-block mb-2 text-center">Биржи покупки</label>
              {this.state.data.exchanges && this.state.data.exchanges.map((exchange, exchangeIndex) =>
                <div key={`exchange-buy-${exchangeIndex}`} className="form-check form-switch form-check-inline">
                  <input
                    className="form-check-input"
                    type="checkbox"
                    checked={this.state.selectedBuyExchanges.includes(exchange)}
                    onChange={() => this.handleExchangeToggle(exchange, 'buy')}
                  />
                  <label className="form-check-label">{exchange}</label>
                </div>
              )}
            </div>
            <div className="form-group mt-2">
              <label className="d-block mb-2 mt-2 text-center">Биржи продажи</label>
              {this.state.data.exchanges && this.state.data.exchanges.map((exchange, exchangeIndex) =>
                <div key={`exchange-sell-${exchangeIndex}`} className="form-check form-switch form-check-inline">
                  <input
                    className="form-check-input"
                    type="checkbox"
                    checked={this.state.selectedSellExchanges.includes(exchange)}
                    onChange={() => this.handleExchangeToggle(exchange, 'sell')}
                  />
                  <label className="form-check-label">{exchange}</label>
                </div>
              )}
            </div>
            <div className="form-group mt-2">
              <label className="mb-2">Монеты</label>
              <Select
                isMulti
                options={coinOptions}
                value={coinOptions.filter(option => this.state.selectedCoins.includes(option.value))}
                onChange={this.handleCoinSelect}
                className="basic-multi-select"
                placeholder="Выберите монеты"
                classNamePrefix="select"
              />
            </div>
            <div className="form-group mt-2">
              <label className="mb-2">Выбранные монеты: </label>
              <div>
                {this.state.selectedCoins.map((coin, coinIndex) => (
                  <span key={`selected-coin-${coinIndex}`} className="badge bg-secondary m-1">
                    {coin}
                    <button type="button" className="btn-close btn-close-white ms-2" aria-label="Close" onClick={() => this.handleCoinRemove(coin)}></button>
                  </span>
                ))}
              </div>
            </div>
            <div className="form-group mt-2">
              <label className="mb-2">Минимальная сумма прибыли</label>
              <input
                type="number"
                name="minProfit"
                className="form-control"
                value={this.state.minProfit}
                onChange={this.handleInputChange}
              />
            </div>
            <div className="form-group mt-2">
              <label className="mb-2">Минимальная сумма сделки</label>
              <input
                type="number"
                name="minDealAmount"
                className="form-control"
                value={this.state.minDealAmount}
                onChange={this.handleInputChange}
              />
            </div>
            <div className="form-group mt-2">
              <label className="mb-2">Максимальная сумма сделки</label>
              <input
                type="number"
                name="maxDealAmount"
                className="form-control"
                value={this.state.maxDealAmount}
                onChange={this.handleInputChange}
              />
            </div>
            <button className="btn btn-primary mt-3" onClick={this.handleFilterSubmit}>Обновить</button>
          </div>
        </div>

        <table className="table table-dark table-bordered mt-5 table-responsive">
          <thead>
            <tr style={{fontSize: '12px'}}>
              <th scope="col">Монета</th>
              <th scope="col">Биржи</th>
              <th scope="col">Объём</th>
              <th scope="col">Спред</th>
              <th scope="col">Цена</th>
              <th scope="col">Диапазон</th>
              <th scope="col">Объём 24ч</th>
              <th scope="col">Ордеры</th>
              <th scope="col">Комиссия</th>
              <th scope="col">Время жизни</th>
              <th scope="col">Название сети</th>
              <th scope="col">Время</th>
              <th scope="col">Подтверждения</th>
              <th scope="col">Маржа</th>
            </tr>
          </thead>
          <tbody style={{fontSize: '12px'}}>
            {this.state.deals && this.state.deals.map((deal, dealIndex) => (
              <React.Fragment key={`deal-${dealIndex}`}>
                <tr onClick={(event) => this.toggleExpand(deal.coin, event)}>
                  <td>
                    <div className="d-flex align-items-center .flex-fill flex-row mb-3">
                        <img src={deal.coinMarketCapLogo} alt={currencyLogo} style={{ width: '16px', marginRight: '5px' }} />
                        <a className="link-underline link-underline-opacity-0 link-light" href={deal.coinMarketCapLink} target="_blank" rel="noopener noreferrer">
                        {deal.coin}
                        </a>
                        {deal.eventData.length > 1 && (
                        <button
                            onClick={(event) => this.toggleExpand(deal.coin, event)}
                            style={{ marginLeft: '10px', cursor: 'pointer', background: 'none', border: 'none', color: 'white' }}
                        >
                            {this.state.expandedCoin === deal.coin ? '-' : '+'}
                        </button>
                        )}
                    </div>
                  </td>
                  <td>
                    <div className="d-flex align-items-center .flex-fill flex-row mb-3">
                      <a className="link-underline link-underline-opacity-0 link-light" href={deal.eventData[0].buyTradingLink} target="_blank" rel="noopener noreferrer">{deal.eventData[0].exchangeForBuy}</a>
                      <a href={deal.eventData[0].withdrawLink} target="_blank" rel="noopener noreferrer" style={{ marginLeft: '10px' }}>
                        <img src={withdrawLogo} alt="withdraw icon" style={{ width: '16px' }} />
                      </a>
                    </div>
                    <div className="d-flex align-items-center .flex-fill flex-row mb-3">
                      <a className="link-underline link-underline-opacity-0 link-light" href={deal.eventData[0].sellTradingLink} target="_blank" rel="noopener noreferrer">{deal.eventData[0].exchangeForSell}</a>
                      <a href={deal.eventData[0].depositLink} target="_blank" rel="noopener noreferrer" style={{ marginLeft: '10px' }}>
                        <img src={depositLogo} alt="deposit icon" style={{ width: '16px' }} />
                      </a>
                    </div>
                  </td>
                    <td>
                        <div>
                            ${deal.eventData[0].fiatVolume} <br /> {deal.eventData[0].coinVolume}
                        </div>
                    </td>
                    <td>
                        <div>
                            ${deal.eventData[0].fiatSpread} <br /> {deal.eventData[0].coinSpread}
                        </div>
                    </td>
                    <td>
                        <div>
                            {deal.eventData[0].averagePriceForBuy} <br /> {deal.eventData[0].averagePriceForSell}
                        </div>
                    </td>
                    <td>
                        <div>
                            {deal.eventData[0].priceRangeForBuy} <br /> {deal.eventData[0].priceRangeForSell}
                        </div>
                    </td>
                    <td>
                        <div>
                            {deal.eventData[0].volume24ExchangeForBuy} <br /> {deal.eventData[0].volume24ExchangeForSell}
                        </div>
                    </td>
                    <td>
                        <div>
                            {deal.eventData[0].ordersCountForBuy} <br /> {deal.eventData[0].ordersCountForSell}
                        </div>
                    </td>
                    <td>
                        <div>
                            {deal.eventData[0].spotFee} <br /> {deal.eventData[0].chainFee}
                        </div>
                    </td>
                    <td>
                        <div>
                            {deal.eventData[0].lifeCycle}
                        </div>
                    </td>
                    <td>
                        <div>
                            {deal.eventData[0].chainName}
                        </div>
                    </td>
                    <td>
                        <div>
                            {deal.eventData[0].transactionTime}
                        </div>
                    </td>
                    <td>
                        <div>
                            {deal.eventData[0].transactionConfirmation}
                        </div>
                    </td>
                    <td>
                        <div>
                            {deal.eventData[0].margin === true && <span role='img' aria-label='margin unlock'>&#128275;</span>}
                            {deal.eventData[0].margin === false && <span role='img' aria-label='margin lock'>&#128274;</span>}
                        </div>
                    </td>
                </tr>
                {this.state.expandedCoin === deal.coin && deal.eventData.slice(1).map((event, eventIndex) => (
                  <tr key={`event-${eventIndex}`}>
                    <td></td>
                    <td>
                      <div>
                        <a className="link-underline link-underline-opacity-0 link-light" href={event.buyTradingLink} target="_blank" rel="noopener noreferrer">{event.exchangeForBuy}</a>
                        <a href={event.withdrawLink} rel="noopener noreferrer" style={{ marginLeft: '10px' }}>
                          <img src={withdrawLogo} alt="withdraw icon" style={{ width: '16px' }} />
                        </a>
                      </div>
                      <div>
                        <a className="link-underline link-underline-opacity-0 link-light" href={event.sellTradingLink} target="_blank" rel="noopener noreferrer">{event.exchangeForSell}</a>
                        <a href={event.depositLink} target="_blank" rel="noopener noreferrer" style={{ marginLeft: '10px' }}>
                          <img src={depositLogo} alt="deposit icon" style={{ width: '16px' }} />
                        </a>
                      </div>
                    </td>
                    <td>${event.fiatVolume} <br />  {event.coinVolume}</td>
                    <td>${event.fiatSpread} <br />  {event.coinSpread}</td>
                    <td>{event.averagePriceForBuy} <br />  {event.averagePriceForSell}</td>
                    <td>{event.priceRangeForBuy} <br />  {event.priceRangeForSell}</td>
                    <td>{event.volume24ExchangeForBuy} <br />  {event.volume24ExchangeForSell}</td>
                    <td>{event.ordersCountForBuy} <br />  {event.ordersCountForSell}</td>
                    <td>{event.spotFee} <br />  {event.chainFee}</td>
                    <td>{event.lifeCycle}</td>
                    <td>{event.chainName}</td>
                    <td>{event.transactionTime}</td>
                    <td>{event.transactionConfirmation}</td>
                    <td>
                      {event.margin === true && <span role='img' aria-label='margin unlock'>&#128275;</span>}
                      {event.margin === false && <span role='img' aria-label='margin lock'>&#128274;</span>}
                    </td>
                  </tr>
                ))}
              </React.Fragment>
            ))}
          </tbody>
        </table>
      </div>
    );
  }
}
