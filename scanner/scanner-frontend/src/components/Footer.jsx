import React from 'react';
import styled from 'styled-components';

const FooterBar = styled.footer`
  background-color: #f0f0f0;
  text-align: center;
  padding: 10px 0;
  left: 0;
  bottom: 0;
  width: 100%;
`;

function Footer() {
  return (
    <FooterBar>
      <p>Copyright © 2024 Все права защищены.</p>
    </FooterBar>
  );
}

export default Footer;