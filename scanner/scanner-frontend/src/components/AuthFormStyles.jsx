import styled, { keyframes } from 'styled-components';

const fadeIn = keyframes`
  0% {
    opacity: 0;
    transform: translate(-50%, -50%);
  }
  100% {
    opacity: 1;
    transform: translate(-50%, -50%);
  }
`;

export const ModalWrapper = styled.div`
    position: absolute;
    left: 0;
    top: 0;
    right: 0;
    bottom: 0;
    width: 100%;
    min-height: 100%;
    backdrop-filter: blur(10px);
`;

export const AuthFormContainer = styled.div`
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  background-color: #333;
  border-radius: 8px;
  box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
  padding: 32px;
  width: 400px;
  animation: ${fadeIn} 0.5s ease-out;
`;

export const FormTitle = styled.h2`
  color: #fff;
  margin-bottom: 24px;
  text-align: center;
`;

export const FormGroup = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  margin-bottom: 16px;
`;

export const FormLabel = styled.label`
  display: block;
  color: #999;
  font-size: 14px;
  margin-bottom: 4px;
`;

export const FormInput = styled.input`
  width: 100%;
  padding: 12px 16px;
  font-size: 16px;
  background-color: #444;
  border: none;
  border-radius: 4px;
  color: #fff;
  transition: background-color 0.3s ease;

  &:focus {
    outline: none;
    background-color: #555;
  }
`;

export const SubmitButton = styled.button`
  width: 100%;
  padding: 12px 16px;
  font-size: 16px;
  background-color: #007bff;
  border: none;
  border-radius: 4px;
  color: #fff;
  cursor: pointer;
  transition: background-color 0.3s ease;

  &:hover {
    background-color: #0056b3;
  }
`;

export const ToggleLink = styled.button`
  display: block;
  margin-top: 16px;
  color: #ea5d3d;
  font-size: 14px;
  text-align: center;
  background-color: transparent;
  border: none;
  cursor: pointer;
  transition: color 0.3s ease;

  &:hover {
    color: #fff;
  }
`;

export const CloseButton = styled.button`
  position: absolute;
  top: 12px;
  right: 12px;
  background-color: transparent;
  border: none;
  cursor: pointer;
  padding: 0;
`;

export const CloseIcon = styled.div`
  width: 20px;
  height: 20px;
  position: relative;
  &::before,
  &::after {
    content: '';
    position: absolute;
    top: 50%;
    left: 50%;
    width: 16px;
    height: 2px;
    background-color: #999;
    transform-origin: center;
    transition: background-color 0.3s ease;
  }
  &::before {
    transform: translate(-50%, -50%) rotate(45deg);
  }
  &::after {
    transform: translate(-50%, -50%) rotate(-45deg);
  }
  &:hover {
    &::before,
    &::after {
      background-color: #fff;
    }
  }
`;
