import * as React from 'react';
import WelcomeContent from './WelcomeContent'
import Dashboard from './Dashboard';
import LoginForm from './LoginForm';
import Buttons from './Buttons';
import { request, setAuthToken } from './axios_helper';


export default class AppContent extends React.Component {

    constructor(props) {
        super(props);
        this.state = {
            componentToShow: "welcome",
            message: "",
            resetRequest: ""
        };
    }

    updateMessage = (message) => {
        this.setState({ message: message });
    };

    updateResetRequest = (data) => {
        this.setState({resetRequest: data});
    };

    login = () => {
        this.setState({componentToShow: "login"});
    };

    logout = () => {
        request(
            "POST",
            "/api/v1/auth/logout"
        ).then(() => {
            this.setState({componentToShow: "welcome"});
        }).catch((error) => {
            this.setState({componentToShow: "welcome"});
        });
    };

    onLogin = (e, username, password) => {
        e.preventDefault();

        request(
            "POST", 
            "/api/v1/auth/sign-in", 
            {
                username: username, 
                password: password
            }
        ).then((response) => {
            setAuthToken(response.data.token);
            this.setState({componentToShow: "dashboard"});
        }).catch((error) => {
            this.setState({componentToShow: "errorSignIn"});
            this.updateMessage(error.response.data.message);
        });
    };

    onRegister = (e, username, email, telegram, password, confirmPassword) => {
        e.preventDefault();
        
        request(
            "POST", 
            "/api/v1/auth/sign-up", 
            {
                username: username,
                email: email,
                telegram: telegram, 
                password: password,
                confirmPassword: confirmPassword,
                roles: ["ROLE_USER"]
            }
        ).then((response) => {
            setAuthToken(response.data.token);
            this.setState({componentToShow: "dashboard"})
        }).catch((error) => {
            this.setState({componentToShow: "errorSignUp"});
            this.updateMessage(error.response.data.message);
        });
    };

    onPasswordReset = (e, username, email, password, confirmPassword) => {
        e.preventDefault();

        request(
            "POST",
            "/api/v1/auth/reset-password",
            {
                username: username,
                email: email,
                password: password,
                confirmPassword: confirmPassword
            }
        ).then((response) => {
            this.setState({componentToShow: "confirmPasswordReset"});
            this.updateResetRequest(response.data.resetRequest);
        }).catch((error) => {
            this.setState({componentToShow: "errorPasswordReset"});
            this.updateMessage(error.response.data.message);
        });
    };

    onConfirmReset = (e, code) => {
        e.preventDefault();
        
        request(
            "POST",
            "/api/v1/auth/confirm-password",
            {
                passwordResetRequest: this.state.resetRequest,
                code: code
            }
        ).then((response) => {
            this.setState({componentToShow: "reset-successful"});
            this.updateMessage(response.data.message)
            this.setState({confirmationEmail: ""});
        }).catch((error) => {
            this.setState({componentToShow: "errorPasswordReset"});
            this.updateMessage(error.response.data.message);
        });
    };
    
    render() {
        return (
            <div className="container-xl">
                <Buttons login={this.login} logout={this.logout} content={this.state.componentToShow}/>
                {this.state.componentToShow === "welcome" && <WelcomeContent/>}
                {this.state.componentToShow === "dashboard" && <Dashboard/>}
                {this.state.componentToShow === "login" && <LoginForm onActive={"login"} onLogin={this.onLogin} onRegister={this.onRegister} onPasswordReset={this.onPasswordReset} onConfirmReset={this.onConfirmReset} onError={false}/>}
                {this.state.componentToShow === "confirmPasswordReset" && <LoginForm onActive={"confirmPasswordReset"} onLogin={this.onLogin} onRegister={this.onRegister} onPasswordReset={this.onPasswordReset} onConfirmReset={this.onConfirmReset} onError={false}/>}
                {this.state.componentToShow === "errorSignUp" && <LoginForm onActive={"register"} onLogin={this.onLogin} onRegister={this.onRegister} onPasswordReset={this.onPasswordReset} onConfirmReset={this.onConfirmReset} onMessage={this.state.message} onError={true}/>}
                {this.state.componentToShow === "errorSignIn" && <LoginForm onActive={"login"} onLogin={this.onLogin} onRegister={this.onRegister} onPasswordReset={this.onPasswordReset} onConfirmReset={this.onConfirmReset} onMessage={this.state.message} onError={true}/>}
                {this.state.componentToShow === "errorPasswordReset" && <LoginForm onActive={"passwordReset"} onLogin={this.onLogin} onRegister={this.onRegister} onPasswordReset={this.onPasswordReset} onConfirmReset={this.onConfirmReset} onMessage={this.state.message} onError={true}/>}
                {this.state.componentToShow === "reset-successful" && <LoginForm onActive={"login"} onLogin={this.onLogin} onRegister={this.onRegister} onPasswordReset={this.onPasswordReset} onConfirmReset={this.onConfirmReset} onMessage={this.state.message} onError={false}/>}
            </div>
        );
    };
}