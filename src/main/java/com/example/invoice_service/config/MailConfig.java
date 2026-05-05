package com.example.invoice_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@Configuration
public class MailConfig {

    @Bean
    public JavaMailSender javaMailSender() {

        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();

        // SMTP server
        mailSender.setHost("smtp.gmail.com");
        mailSender.setPort(587);

        // Gmail account
        mailSender.setUsername("hieppnguyenn.dev@gmail.com");

        // App password của Gmail
        mailSender.setPassword("lrpm fdvr tese nwnl");

        // Encoding
        mailSender.setDefaultEncoding("UTF-8");

        // SMTP properties
        Properties props = mailSender.getJavaMailProperties();

        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.debug", "false");

        return mailSender;
    }
}
