package com.alertas.sms.service.serviceImpl;

import com.alertas.sms.service.EnvioSms;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// sin llaves de Twilio: no envia nada, solo lo deja en el log (y en sms_envios como enviado)
public class RegistroEnvioSms implements EnvioSms {

    private static final Logger LOG = LoggerFactory.getLogger(RegistroEnvioSms.class);

    @Override
    public ResultadoEnvio enviar(String celular, String texto) {

        LOG.info("SMS (sin proveedor) a {}: {}", celular, texto);
        return new ResultadoEnvio(true, "registro-" + UUID.randomUUID(), null);
    }
}
