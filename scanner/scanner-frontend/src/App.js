import React from 'react';
import { BrowserRouter as Router, Route, Routes } from 'react-router-dom';
import Header from './components/Header';
import Footer from './components/Footer';
import IndexPage from './pages/IndexPage';
import DashboardPage from './pages/DashboardPage';
import styled from 'styled-components';

const MainContent = styled.div`
  padding-top: 20px;
  min-height: 80vh; // Adjust based on header and footer size
`;

function App() {
  const handleLogout = () => {
    // Реализация выхода пользователя
    console.log("Выход выполнен");
  };

  return (
    <Router>
      <Header onLogout={handleLogout} />
      <MainContent>
        <Routes>
          <Route path="/" exact element={<IndexPage />} />
          <Route path="/dashboard" exact element={<DashboardPage />} />
        </Routes>
      </MainContent>
      <Footer />
    </Router>
  );
}

export default App;