import * as React from 'react';
import { request } from './axios_helper';

export default class Dashboard extends React.Component {

    constructor(props) {
        super(props);
        this.state = {
            data: [],
            deals: [],
            expandedCoin: null,
        }
        this.intervalId = null;
    };

    fetchArbitrageEvents = () => {
        request(
            "GET",
            "/api/v1/app/refresh-data",
            {}
        ).then((response) => {
            this.setState({deals: response.data})
        });
    }

    componentDidMount() {
        request(
            "GET",
            "/api/v1/app/get-exchanges",
            {}
        ).then((response) => {
            this.setState({data : response.data})
            this.fetchArbitrageEvents();
        });

        this.intervalId = setInterval(this.fetchArbitrageEvents, 5000);
    };

    componentWillUnmount() {
        clearInterval(this.intervalId);
    }

    toggleExpand = (coin, event) => {
        event.stopPropagation();
        this.setState((prevState) => ({
            expandedCoin: prevState.expandedCoin === coin ? null : coin
        }));
    }

    render() {
        return (
            <div className="row justify-content-md-center">
                <h6 className="text-center">Биржи покупки</h6>
                <div>
                    {this.state.data && this.state.data.map((exchange, exchangeIndex) => 
                        <React.Fragment key={`exchange-buy-${exchangeIndex}`}>
                            <div className="form-check form-switch form-check-inline">
                                <input className="form-check-input" type="checkbox" role="switch" id="flexSwitchCheckDefault"/>
                                <label className="form-check-label" htmlFor="flexSwitchCheckDefault">{exchange}</label>
                            </div>
                        </React.Fragment>
                    )}
                </div>
                <h6 className="text-center">Биржи продажи</h6>
                <div>
                    {this.state.data && this.state.data.map((exchange, exchangeIndex) => 
                    <React.Fragment key={`exchange-sell-${exchangeIndex}`}>
                        <div className="form-check form-switch form-check-inline">
                            <input className="form-check-input" type="checkbox" role="switch" id="flexSwitchCheckDefault"/>
                            <label className="form-check-label" htmlFor="flexSwitchCheckDefault">{exchange}</label>
                        </div>
                    </React.Fragment>
                    )}
                </div>
                <table className="table table-dark table-bordered" style={{marginTop: '50px', fontSize: '12px'}}>
                    <thead>
                        <tr className='fs-9'>
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
                            <th scope="col">Фьючерсы</th>
                        </tr>
                    </thead>
                    <tbody>
                        {this.state.deals && this.state.deals.map((deal, dealIndex) => (
                            <React.Fragment key={`deal-${dealIndex}`}>
                                <tr onClick={() => this.toggleExpand(deal.coin)}>
                                    <td>
                                        {deal.coin}
                                        {deal.eventData.length > 1 && (
                                            <button 
                                                onClick={(event) => this.toggleExpand(deal.coin, event)}
                                                style={{ marginLeft: '10px', cursor: 'pointer', background: 'none', border: 'none', color: 'white' }}
                                            >
                                                {this.state.expandedCoin === deal.coin ? '-' : '+'}
                                            </button>
                                        )}
                                    </td>
                                    <td>{deal.eventData[0].exchangeForBuy} <br /> {deal.eventData[0].exchangeForSell}</td>
                                    <td>${deal.eventData[0].fiatVolume} <br /> {deal.eventData[0].coinVolume}</td>
                                    <td>${deal.eventData[0].fiatSpread} <br /> {deal.eventData[0].coinSpread}</td>
                                    <td>{deal.eventData[0].averagePriceForBuy} <br /> {deal.eventData[0].averagePriceForSell}</td>
                                    <td>{deal.eventData[0].priceRangeForBuy} <br /> {deal.eventData[0].priceRangeForSell}</td>
                                    <td>{deal.eventData[0].volume24ExchangeForBuy} <br /> {deal.eventData[0].volume24ExchangeForSell}</td>
                                    <td>{deal.eventData[0].ordersCountForBuy} <br /> {deal.eventData[0].ordersCountForSell}</td>
                                    <td>{deal.eventData[0].spotFee} <br /> {deal.eventData[0].spotFee}</td>
                                    <td>{deal.eventData[0].arbitrageEventLifetime}</td>
                                    <td>{deal.eventData[0].chainName}</td>
                                    <td>{deal.eventData[0].transactionTime}</td>
                                    <td>{deal.eventData[0].transactionConfirmation}</td>
                                    <td>{deal.eventData[0].margin}</td>
                                    <td>{deal.eventData[0].futures}</td>
                                </tr>
                                {this.state.expandedCoin === deal.coin && deal.eventData.slice(1).map((event, eventIndex) => (
                                    <tr key={`event-${eventIndex}`}>
                                        <td></td>
                                        <td>
                                            {event.exchangeForBuy} 
                                            <br /> 
                                            {event.exchangeForSell}
                                        </td>
                                        <td>
                                            ${event.fiatVolume} 
                                            <br />  
                                            {event.coinVolume}
                                        </td>
                                        <td>
                                            ${event.fiatSpread} 
                                            <br />  
                                            {event.coinSpread}
                                        </td>
                                        <td>
                                            {event.averagePriceForBuy} 
                                            <br />  
                                            {event.averagePriceForSell}
                                        </td>
                                        <td>
                                            {event.priceRangeForBuy} 
                                            <br />  
                                            {event.priceRangeForSell}
                                        </td>
                                        <td>
                                            {event.volume24ExchangeForBuy} 
                                            <br />  
                                            {event.volume24ExchangeForSell} 
                                        </td>
                                        <td>
                                            {event.ordersCountForBuy} 
                                            <br />  
                                            {event.ordersCountForSell}
                                        </td>
                                        <td>
                                            {event.spotFee} 
                                            <br />  
                                            {event.spotFee}
                                        </td>
                                        <td>{event.arbitrageEventLifetime}</td>
                                        <td>{event.chainName}</td>
                                        <td>{event.transactionTime}</td>
                                        <td>{event.transactionConfirmation}</td>
                                        <td>{event.margin}</td>
                                        <td>{event.futures}</td>
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