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
            componentToShow: "welcome"
        };
    }

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
            console.log(error);
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
            this.setState({componentToShow: "dashboard"})
        }).catch((error) => {
            this.setState({componentToShow: "welcome"})
        });
    };

    onRegister = (e, username, password, confirmPassword) => {
        e.preventDefault();

        request(
            "POST", 
            "/api/v1/auth/sign-up", 
            {
                username: username, 
                password: password,
                roles: ["ROLE_USER"]
            }
        ).then((response) => {
            setAuthToken(response.data.token);
            this.setState({componentToShow: "dashboard"})
        }).catch((error) => {
            this.setState({componentToShow: "welcome"})
        });
    };
    
    render() {
        return (
            <div className="container-xl">
                <Buttons login={this.login} logout={this.logout}/>
                {this.state.componentToShow === "welcome" && <WelcomeContent/>}
                {this.state.componentToShow === "dashboard" && <Dashboard/>}
                {this.state.componentToShow === "login" && <LoginForm onLogin={this.onLogin} onRegister={this.onRegister}/>}
            </div>
        );
    };
}