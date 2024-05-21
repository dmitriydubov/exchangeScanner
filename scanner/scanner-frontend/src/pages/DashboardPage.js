import React from 'react';
import styled from 'styled-components';

const Table = styled.table`
  width: 100%;
  border-collapse: collapse;

  th, td {
    border: 1px solid #dddddd;
    text-align: left;
    padding: 8px;
  }

  th {
    background-color: #f2f2f2;
  }
`;

const ContentArea = styled.div`
  padding: 20px;
`;

function DashboardPage() {
  return (
    <ContentArea>
      <Table>
        <thead>
          <tr>
            <th>Монеты</th>
            <th>Биржи</th>
            <th>Объём</th>
            <th>Объём 24ч</th>
            <th>Спред</th>
            <th>Цена</th>
            <th>Диапазон</th>
            <th>Ордеры</th>
            <th>Комиссия спота</th>
            <th>Комиссия сети</th>
            <th>Сеть</th>
            <th>Время жизни</th>
          </tr>
        </thead>
        <tbody>
          {/* Здесь данные для таблицы */}
        </tbody>
      </Table>
    </ContentArea>
  );
}

export default DashboardPage;