import React, { useState } from 'react';
import styled from 'styled-components';
import { Link } from 'react-router-dom';
import AuthForm from './AuthForm';
import Logo from './img/site-logo.png';
import './HeaderStyles.css';

const Nav = styled.nav`
  display: flex;
  justify-content: center;
  align-items: center;
  background-color: #43423e;
  padding: 10px;
  text-align: center;
`;

const HeaderButton = styled.button`
  margin: 5px;
  padding: 8px 16px;
  font-size: 1em;
  background-color: #ea5d3d;
  color: white;
  border: none;
  border-radius: 5px;
  cursor: pointer;
  transition: background-color 0.3s ease;

  &:hover {
    background-color: #c2482c;
  }
`;

function Header({ onLogout }) {
  const [showAuthForm, setShowAuthForm] = useState(false);

  const toggleAuthForm = () => {
    setShowAuthForm(!showAuthForm);
  };

  const closeAuthForm = () => {
    setShowAuthForm(false);
  };

  return (
    <header>
      <Nav>
        <Link to="/" style={{ marginRight: 'auto'}}>
          <div className="logo-container">
            <img className="logo" src={Logo} alt="logo"></img>
          </div>
        </Link>
        <Link to="/">
          <HeaderButton>Главная</HeaderButton>
        </Link>
        <Link to="/dashboard">
          <HeaderButton>Сканер</HeaderButton>
        </Link>
        <HeaderButton onClick={toggleAuthForm} style={{ marginLeft: 'auto'}}>Войти</HeaderButton>
        {showAuthForm && <AuthForm onClose={closeAuthForm} />}
      </Nav>
    </header>
  );
}

export default Header;