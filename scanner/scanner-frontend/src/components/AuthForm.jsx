import React, { useState } from 'react';
import {
  ModalWrapper,
  AuthFormContainer,
  FormTitle,
  FormGroup,
  FormLabel,
  FormInput,
  SubmitButton,
  ToggleLink,
  CloseButton,
  CloseIcon
} from './AuthFormStyles';

const AuthForm = ({ onClose }) => {
  const [isLoginForm, setIsLoginForm] = useState(true);
  const [formData, setFormData] = useState({
    email: '',
    password: '',
    confirmPassword: '',
  });

  const handleInputChange = (e) => {
    setFormData({ ...formData, [e.target.name]: e.target.value });
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    let response;

    if (!isLoginForm && (formData.password !== formData.confirmPassword)) {
      console.log("Пароли не совпадают");
      return;
    }

    if (isLoginForm) {
      response = fetch('/api/v1/auth/sign-in', {
        method: 'POST',
        headers: {
          'Accept': 'application/json',
          'Content-Type': 'application/json',
          'Host': 'localhost:8080',
          'Accept-Encoding': 'gzip, deflate, br',
          'Connection': 'keep-alive',
        },
        body: JSON.stringify({
          username: formData.email,
          password: formData.password,
        }),
      });
    } else {
      response = fetch('/api/v1/auth/sign-up', {
        method: 'POST',
        headers: {
          'Accept': 'application/json',
          'Content-Type': 'application/json',
          'Host': 'localhost:8080',
          'Accept-Encoding': 'gzip, deflate, br',
          'Connection': 'keep-alive',
        },
        body: JSON.stringify({
          username: formData.email,
          password: formData.password,
          roles: ['ROLE_USER'],
        }),
      });
    }
    
    if (response.ok) {
      console.log(response);
    } else {
      console.log('Error', response);
    }
  };

  const toggleForm = () => {
    setIsLoginForm(!isLoginForm);
  };

  const closeAuthForm = () => {
    onClose();
  };

  return (
    <ModalWrapper>
      <AuthFormContainer>
        <CloseButton onClick={closeAuthForm}>
          <CloseIcon />
        </CloseButton>
        <FormTitle>{isLoginForm ? 'Авторизация' : 'Регистрация'}</FormTitle>
        <form onSubmit={handleSubmit}>
          <FormGroup>
            <FormLabel htmlFor="email">Email:</FormLabel>
            <FormInput
              type="email"
              id="email"
              name="email"
              value={formData.email}
              onChange={handleInputChange}
              required
            />
          </FormGroup>
          <FormGroup>
            <FormLabel htmlFor="password">Пароль:</FormLabel>
            <FormInput
              type="password"
              id="password"
              name="password"
              value={formData.password}
              onChange={handleInputChange}
              required
            />
          </FormGroup>
          {!isLoginForm && (
            <FormGroup>
              <FormLabel htmlFor="password">Подтвердите пароль:</FormLabel>
              <FormInput
                type="password"
                id="confirmPassword"
                name="confirmPassword"
                value={formData.confirmPassword}
                onChange={handleInputChange}
                required
              />
            </FormGroup>
          )}
          <SubmitButton type="submit">
            {isLoginForm ? 'Войти' : 'Зарегистрироваться'}
          </SubmitButton>
          <ToggleLink onClick={toggleForm}>
            {isLoginForm
              ? 'Нет аккаунта? Зарегистрироваться'
              : 'Уже есть аккаунт? Войти'}
          </ToggleLink>
        </form>
      </AuthFormContainer>
    </ModalWrapper>
  );
};

export default AuthForm;
