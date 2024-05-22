import * as React from 'react';
import { request } from './axios_helper';

export default class Dashboard extends React.Component {

    constructor(props) {
        super(props);
        this.state = {
            data: []
        }
    };

    componentDidMount() {
        request(
            "GET",
            "/api/v1/app/exchanges",
            {}
        ).then((response) => {
            this.setState({data : response.data})
        });
    };

    render() {
        return (
            <div className="row justify-content-md-center">
                <h6 className="text-center">Биржи покупки</h6>
                <div>
                    {this.state.data && this.state.data.map((exchange) => 
                        <div className="form-check form-switch form-check-inline">
                            <input className="form-check-input" type="checkbox" role="switch" id="flexSwitchCheckDefault"/>
                            <label className="form-check-label" for="flexSwitchCheckDefault">{exchange.name}</label>
                        </div>
                    )}
                </div>
                <h6 className="text-center">Биржи продажи</h6>
                <div>
                    {this.state.data && this.state.data.map((exchange) => 
                        <div className="form-check form-switch form-check-inline">
                            <input className="form-check-input" type="checkbox" role="switch" id="flexSwitchCheckDefault"/>
                            <label className="form-check-label" for="flexSwitchCheckDefault">{exchange.name}</label>
                        </div>
                    )}
                </div>
                <table class="table table-dark table-bordered" style={{marginTop: '50px'}}>
                    <thead>
                        <tr>
                            <th scope="col">Монета</th>
                            <th scope="col">Биржи</th>
                            <th scope="col">Объём</th>
                            <th scope="col">Спред</th>
                            <th scope="col">Цена</th>
                            <th scope="col">Сеть</th>
                            <th scope="col">Время жизни</th>
                            <th scope="col">Объём 24ч</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr>
                            <td>FTM</td>
                            <td>
                                <tr>MEXC</tr>
                                <tr>CoinEx</tr>
                            </td>
                            <td>
                                <tr>$203,318.6</tr>
                                <tr>256.4K</tr>
                            </td>
                            <td>
                                <tr>$185,7</tr>
                                <tr>0.09%</tr>
                            </td>
                            <td>
                                <tr>76025</tr>
                                <tr>76400</tr>
                            </td>
                            <td>FTM</td>
                            <td>01:32</td>
                            <td>$300,000</td>
                        </tr>
                        <tr>
                            <td>POPCAT</td>
                            <td>
                                <tr>CoinEX</tr>
                                <tr>MEXC</tr>
                            </td>
                            <td>
                                <tr>$1,635.8</tr>
                                <tr>3.94K</tr>
                            </td>
                            <td>
                                <tr>$5,5</tr>
                                <tr>0.33%</tr>
                            </td>
                            <td>
                                <tr>41537</tr>
                                <tr>41942</tr>
                            </td>
                            <td>SOL</td>
                            <td>00:39</td>
                            <td>$100,000</td>
                        </tr>
                    </tbody>
                </table>
            </div>
        );
    }
}