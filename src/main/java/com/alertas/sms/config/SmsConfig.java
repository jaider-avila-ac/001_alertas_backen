package com.alertas.sms.config;

import com.alertas.sms.service.EnvioSms;
import com.alertas.sms.service.serviceImpl.RegistroEnvioSms;
import com.alertas.sms.service.serviceImpl.TwilioEnvioSms;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

// con las tres llaves de Twilio se usa Twilio; si falta alguna, no se envia nada (solo registro).
// para activarlo solo hay que llenar TWILIO_SID, TWILIO_TOKEN y TWILIO_NUMERO en el .env
@Configuration
@EnableAsync
public class SmsConfig {

    private static final Logger LOG = LoggerFactory.getLogger(SmsConfig.class);

    @Bean
    public EnvioSms envioSms(
            @Value("${app.sms.twilio.sid:}") String sid,
            @Value("${app.sms.twilio.token:}") String token,
            @Value("${app.sms.twilio.numero:}") String numero,
            ObjectMapper mapper) {

        if (sid.isBlank() || token.isBlank() || numero.isBlank()) {
            LOG.info("SMS sin proveedor: faltan las llaves de Twilio, los mensajes solo se registran");
            return new RegistroEnvioSms();
        }

        LOG.info("SMS con Twilio desde {}", numero);
        return new TwilioEnvioSms(sid.trim(), token.trim(), numero.trim(), mapper);
    }

    // pocos hilos: los sms no son urgentes y asi no se satura el proveedor
    @Bean(name = "envioSmsExecutor")
    public Executor envioSmsExecutor() {

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("sms-");
        executor.initialize();
        return executor;
    }
}
