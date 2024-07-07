import * as React from 'react';

export default class WelcomeContent extends React.Component {
    render () {
        return (
            <div className="row justify-content-md-center">
                <div className="jumbotron jumbotron-fluid">
                    <div className="container text-center">
                        <h2 className="display-4">Добро пожаловать!</h2>
                        <p className="lead">Основная информация</p>
                    </div>
                </div>
            </div>
        );
    };
}