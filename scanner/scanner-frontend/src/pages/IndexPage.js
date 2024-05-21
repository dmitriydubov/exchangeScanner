import React from 'react';
import styled from 'styled-components';

const ContentArea = styled.div`
  padding: 20px;
  text-align: center;
`;

function IndexPage() {
  return (
    <ContentArea>
      <h2>Добро пожаловать!</h2>
      <p>Здесь информация о сайте и услугах.</p>
      {/* Форма регистрации / авторизации */}
    </ContentArea>
  );
}

export default IndexPage;