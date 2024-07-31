import classNames from 'classnames';
import * as React from 'react';



export default class LoginForm extends React.Component {
    
    constructor(props) {
        super(props);
        this.state = {
            active: props.onActive,
            login: "",
            email: "",
            telegram: "",
            password: "",
            confirmPassword: "",
            code: "",
            onLogin: props.onLogin,
            onRegister: props.onRegister,
            onPasswordReset: props.onPasswordReset,
            onConfirmReset: props.onConfirmReset,
            message: props.onMessage
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

    onSubmitPasswordReset = (e) => {
        this.state.onPasswordReset(e, this.state.login, this.state.email, this.state.password, this.state.confirmPassword);
    }

    onSubmitConfirmReset = (e) => {
        this.state.onConfirmReset(e, this.state.code);
    }
    
    render() {
        return (
            <div className="row justify-content-center">
                <div className="col-6">
                    <ul className="nav nav-pills nav-justified mb-3" id="ex1" role="tablist">
                        <li className="nav-item" role="presentation">
                            <button id="tab-login" className={classNames("nav-link", this.state.active === "login" ? "active" : "")} onClick={() => this.setState({active: "login", message: undefined})}>Войти</button>
                        </li>
                        <li className="nav-item" role="presentation">
                            <button id="tab-register" className={classNames("nav-link", this.state.active === "register" ? "active" : "")} onClick={() => this.setState({active: "register", message: undefined})}>Зарегистрироваться</button>
                        </li>
                        <li className="nav-item" role="presentation">
                            <button id="tab-passwordReset" className={classNames("nav-link", this.state.active === "passwordReset" ? "active" : "")} onClick={() => this.setState({active: "passwordReset", message: undefined})}>Восстановление пароля</button>
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
                            {this.state.message !== undefined && this.props.onError &&
                                <div className='alert alert-danger d-flex align-items-center' role="alert">
                                    <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" fill="currentColor" className='bi bi-exclamation-triangle-fill flex-shrink-0 me-2' viewBox="0 0 16 16" role="img" aria-label="Warning:">
                                        <path d="M8.982 1.566a1.13 1.13 0 0 0-1.96 0L.165 13.233c-.457.778.091 1.767.98 1.767h13.713c.889 0 1.438-.99.98-1.767L8.982 1.566zM8 5c.535 0 .954.462.9.995l-.35 3.507a.552.552 0 0 1-1.1 0L7.1 5.995A.905.905 0 0 1 8 5zm.002 6a1 1 0 1 1 0 2 1 1 0 0 1 0-2z"/>
                                    </svg>
                                    <div>
                                        {this.props.onMessage}
                                    </div>
                                </div>
                            }
                            {this.state.message !== undefined && !this.props.onError &&
                                <div className='alert alert-success d-flex align-items-center' role="alert">
                                    {this.props.onMessage}
                                </div>
                            } 
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
                                    <input type="password" id="loginPasswordRegConfirm" name="confirmPassword" className="form-control" onChange={this.onChangeHandler}/>
                                    <label className="form-label" htmlFor="loginPasswordRegConfirm">Подтвердите Пароль</label>
                                </div>
                                <button type="submit" className="btn btn-primary btn-block mb-4">Зарегистрироваться</button>
                            </form>
                            {this.state.message !== undefined && this.props.onError &&
                                <div className='alert alert-danger d-flex align-items-center' role="alert">
                                    <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" fill="currentColor" className='bi bi-exclamation-triangle-fill flex-shrink-0 me-2' viewBox="0 0 16 16" role="img" aria-label="Warning:">
                                        <path d="M8.982 1.566a1.13 1.13 0 0 0-1.96 0L.165 13.233c-.457.778.091 1.767.98 1.767h13.713c.889 0 1.438-.99.98-1.767L8.982 1.566zM8 5c.535 0 .954.462.9.995l-.35 3.507a.552.552 0 0 1-1.1 0L7.1 5.995A.905.905 0 0 1 8 5zm.002 6a1 1 0 1 1 0 2 1 1 0 0 1 0-2z"/>
                                    </svg>
                                    <div>
                                        {this.props.onMessage}
                                    </div>
                                </div>
                            } 
                        </div>
                        <div className={classNames("tab-pane", "fade", this.state.active === "passwordReset" ? "show active" : "")} id="pills-passwordReset">
                            <form onSubmit={this.onSubmitPasswordReset}>
                                <div className="form-outline mb-4">
                                    <input type="text" id="loginNamePasswordReset" name="login" autoComplete='off' className="form-control" onChange={this.onChangeHandler}/>
                                    <label className="form-label" htmlFor="loginNamePasswordReset">Имя пользователя</label>
                                </div>
                                <div className="form-outline mb-4">
                                    <input type="text" id="emailConfirm" name="email" autoComplete='off' className="form-control" onChange={this.onChangeHandler}/>
                                    <label className="form-label" htmlFor="emailConfirm">Укажите почту</label>
                                </div>
                                <div className="form-outline mb-4">
                                    <input type="password" id="loginPasswordReset" name="password" className="form-control" onChange={this.onChangeHandler}/>
                                    <label className="form-label" htmlFor="loginPasswordReset">Введите новый пароль</label>
                                </div>
                                <div className="form-outline mb-4">
                                    <input type="password" id="loginPasswordResetRepeat" name="confirmPassword" className="form-control" onChange={this.onChangeHandler}/>
                                    <label className="form-label" htmlFor="loginPasswordResetRepeat">Повторите пароль</label>
                                </div>
                                <button type="submit" className="btn btn-primary btn-block mb-4">Подтвердить</button>
                            </form>
                            {this.state.message !== undefined && this.props.onError &&
                                <div className='alert alert-danger d-flex align-items-center' role="alert">
                                    <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" fill="currentColor" className='bi bi-exclamation-triangle-fill flex-shrink-0 me-2' viewBox="0 0 16 16" role="img" aria-label="Warning:">
                                        <path d="M8.982 1.566a1.13 1.13 0 0 0-1.96 0L.165 13.233c-.457.778.091 1.767.98 1.767h13.713c.889 0 1.438-.99.98-1.767L8.982 1.566zM8 5c.535 0 .954.462.9.995l-.35 3.507a.552.552 0 0 1-1.1 0L7.1 5.995A.905.905 0 0 1 8 5zm.002 6a1 1 0 1 1 0 2 1 1 0 0 1 0-2z"/>
                                    </svg>
                                    <div>
                                        {this.props.onMessage}
                                    </div>
                                </div>
                            } 
                        </div>
                        <div className={classNames("tab-pane", "fade", this.state.active === "confirmPasswordReset" ? "show active" : "")} id="pills-passwordReset">
                            <form onSubmit={this.onSubmitConfirmReset}>
                                <div className="form-outline mb-4">
                                    <input type="text" id="recoveryCode" name="code" autoComplete='off' className="form-control" onChange={this.onChangeHandler}/>
                                    <label className="form-label" htmlFor="recoveryCode">Введите код отправленный на указанный e-mail</label>
                                </div>
                                <button type="submit" className="btn btn-primary btn-block mb-4">Подтвердить</button>
                            </form>
                            {this.state.message !== undefined && this.props.onError &&
                                <div className='alert alert-danger d-flex align-items-center' role="alert">
                                    <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" fill="currentColor" className='bi bi-exclamation-triangle-fill flex-shrink-0 me-2' viewBox="0 0 16 16" role="img" aria-label="Warning:">
                                        <path d="M8.982 1.566a1.13 1.13 0 0 0-1.96 0L.165 13.233c-.457.778.091 1.767.98 1.767h13.713c.889 0 1.438-.99.98-1.767L8.982 1.566zM8 5c.535 0 .954.462.9.995l-.35 3.507a.552.552 0 0 1-1.1 0L7.1 5.995A.905.905 0 0 1 8 5zm.002 6a1 1 0 1 1 0 2 1 1 0 0 1 0-2z"/>
                                    </svg>
                                    <div>
                                        {this.props.onMessage}
                                    </div>
                                </div>
                            } 
                        </div>
                    </div>
                </div>
            </div>
        );
    };
}