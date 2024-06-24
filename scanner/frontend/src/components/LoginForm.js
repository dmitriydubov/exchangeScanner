import classNames from 'classnames';
import * as React from 'react';



export default class LoginForm extends React.Component {
    
    constructor(props) {
        super(props);
        this.state = {
            active: "login",
            login: "",
            email: "",
            telegram: "",
            password: "",
            confirmPassword: "",
            onLogin: props.onLogin,
            onRegister: props.onRegister
        };
    }

    onChangeHandler = (event) => {
        let name = event.target.name;
        let value = event.target.value;

        this.setState({[name] : value});
    };

    onSubmitLogin = (e) => {
        this.state.onLogin(e, this.state.login, this.state.password);
    };

    onSubmitRegister = (e) => {
        this.state.onRegister(e, this.state.login, this.state.email, this.state.telegram, this.state.password, this.state.confirmPassword);
    };
    
    render() {
        return (
            <div className="row justify-content-center">
                <div className="col-4">
                    <ul className="nav nav-pills nav-justified mb-3" id="ex1" role="tablist">
                        <li className="nav-item" role="presentation">
                            <button id="tab-login" className={classNames("nav-link", this.state.active === "login" ? "active" : "")} onClick={() => this.setState({active: "login"})}>Войти</button>
                        </li>
                        <li className="nav-item" role="presentation">
                            <button id="tab-register" className={classNames("nav-link", this.state.active === "register" ? "active" : "")} onClick={() => this.setState({active: "register"})}>Зарегистрироваться</button>
                        </li>
                    </ul>
                    <div className="tab-content">
                        <div className={classNames("tab-pane", "fade", this.state.active === "login" ? "show active" : "")} id="pills-login">
                            <form onSubmit={this.onSubmitLogin}>
                                <div className="form-outline mb-4">
                                    <input type="text" id="loginName" name="login" autoComplete='off' className="form-control" onChange={this.onChangeHandler}/>
                                    <label className="form-label" htmlFor="loginName">Имя пользователя</label>
                                </div>
                                <div className="form-outline mb-4">
                                    <input type="password" id="loginPassword" name="password" className="form-control" onChange={this.onChangeHandler}/>
                                    <label className="form-label" htmlFor="loginPassword">Пароль</label>
                                </div>
                                <button type="submit" className="btn btn-primary btn-block mb-4">Войти</button>
                            </form>
                        </div>
                        <div className={classNames("tab-pane", "fade", this.state.active === "register" ? "show active" : "")} id="pills-register">
                            <form onSubmit={this.onSubmitRegister}>
                                <div className="form-outline mb-4">
                                    <input type="text" id="loginNameReg" name="login" autoComplete='off' className="form-control" onChange={this.onChangeHandler}/>
                                    <label className="form-label" htmlFor="loginNameReg">Имя пользователя</label>
                                </div>
                                <div className="form-outline mb-4">
                                    <input type="email" id="emailReg" name="email" autoComplete='off' className="form-control" onChange={this.onChangeHandler}/>
                                    <label className="form-label" htmlFor="emailReg">E-mail</label>
                                </div>
                                <div className="form-outline mb-4">
                                    <input type="text" id="telegramReg" name="telegram" autoComplete='off' className="form-control" onChange={this.onChangeHandler}/>
                                    <label className="form-label" htmlFor="telegramReg">Имя аккаунта в Telegram</label>
                                </div>
                                <div className="form-outline mb-4">
                                    <input type="password" id="loginPasswordReg" name="password" className="form-control" onChange={this.onChangeHandler}/>
                                    <label className="form-label" htmlFor="loginPasswordReg">Пароль</label>
                                </div>
                                <div className="form-outline mb-4">
                                    <input type="password" id="loginPasswordRegConfirm" name="password" className="form-control" onChange={this.onChangeHandler}/>
                                    <label className="form-label" htmlFor="loginPasswordRegConfirm">Подтвердите Пароль</label>
                                </div>
                                <button type="submit" className="btn btn-primary btn-block mb-4">Зарегистрироваться</button>
                            </form>
                        </div>
                    </div>
                </div>
            </div>
        );
    };
}