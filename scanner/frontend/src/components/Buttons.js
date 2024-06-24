import * as React from 'react';


export default function Buttons(props) {
    return (
        <div className="row">
            <div className="col-md-12 text-center" style={{marginTop: "30px"}}>
                {props.content === "welcome" && <button className="btn btn-primary" style={{margin: "10px"}} onClick={props.login}>Войти</button>}
                {props.content === "dashboard" && <button className="btn btn-dark" style={{margin: "10px"}} onClick={props.logout}>Выйти</button>}
            </div>
        </div>
    );
};