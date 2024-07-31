package com.exchange.scanner.security.service.impl;

import com.exchange.scanner.security.model.ConfirmationCode;
import com.exchange.scanner.security.repository.ConfirmationCoinRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.MailMessage;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class MailService {

    @Autowired
    private MailSender mailSender;

    @Autowired
    private MailMessage mailMessage;

    private final ConfirmationCoinRepository confirmationCoinRepository;

    public void sendEmail(String subject, String emailAddress) {
        mailMessage.setSubject(subject);
        mailMessage.setFrom("scanner-arbitrage@mail.ru");
        mailMessage.setTo(emailAddress);
        String code = generateCode();
        mailMessage.setText(code);

        try {
            mailSender.send((SimpleMailMessage) mailMessage);
            ConfirmationCode confirmationCode = new ConfirmationCode();
            confirmationCode.setEmail(emailAddress);
            confirmationCode.setCode(code);
            confirmationCode.setTimestamp(String.valueOf(System.currentTimeMillis()));
            confirmationCoinRepository.save(confirmationCode);
        } catch (Exception ex) {
            log.error("Ошибка отправки письма с кодом подтверждения. Причина: {}", ex.getLocalizedMessage());
        }
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < 4; i++) {
            sb.append(Math.round(Math.random() * 9));
        }

        return sb.toString();
    }
}
