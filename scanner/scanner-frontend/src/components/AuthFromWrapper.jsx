import React from 'react';
import styled from 'styled-components';

const AuthFormWrapper = styled.div`
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  display: flex;
  justify-content: center;
  align-items: center;
  background-color: rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(10px);
  z-index: 1000;
`;

const AuthForm = ({ onClose, children }) => {
  return (
    <AuthFormWrapper>
      {children}
    </AuthFormWrapper>
  );
};

export default AuthForm;
