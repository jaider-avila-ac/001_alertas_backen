package com.alertas.sms.service.serviceImpl;

import com.alertas.shared.TenantSupport;
import com.alertas.shared.interceptor.TenantContext;
import com.alertas.sms.model.SmsEnvio;
import com.alertas.sms.repository.SmsEnvioRepository;
import com.alertas.sms.service.EnvioSms;
import com.alertas.sms.service.SmsDespachoService;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class SmsDespachoServiceImpl implements SmsDespachoService {

    private final SmsEnvioRepository repository;
    private final EnvioSms envioSms;
    private final TransactionTemplate transaccion;
    private final EntityManager em;

    public SmsDespachoServiceImpl(
            SmsEnvioRepository repository,
            EnvioSms envioSms,
            PlatformTransactionManager transacciones,
            EntityManager em) {

        this.repository = repository;
        this.envioSms = envioSms;
        this.transaccion = new TransactionTemplate(transacciones);
        this.em = em;
    }

    // corre en otro hilo: aqui no hay solicitud, la institucion se fija a mano para que RLS deje ver los sms
    @Override
    @Async("envioSmsExecutor")
    public void enviar(Long institucionId, List<Long> smsIds) {

        try {
            for (Long smsId : smsIds) {
                // uno por transaccion: si uno falla los demas siguen
                transaccion.executeWithoutResult(estado -> enviarUno(institucionId, smsId));
            }
        } finally {
            TenantContext.limpiar();
        }
    }

    private void enviarUno(Long institucionId, Long smsId) {

        TenantSupport.usarInstitucion(em, institucionId);

        SmsEnvio sms = repository.findById(smsId).orElse(null);

        if (sms == null || !SmsEnvio.PENDIENTE.equals(sms.getEstado())) {
            return;
        }

        EnvioSms.ResultadoEnvio resultado = envioSms.enviar("+57" + sms.getCelular(), sms.getMensaje());

        if (resultado.ok()) {
            sms.setEstado(SmsEnvio.ENVIADO);
            sms.setProveedorId(resultado.proveedorId());
            sms.setEnviadoEn(OffsetDateTime.now());
        } else {
            sms.setEstado(SmsEnvio.FALLIDO);
            sms.setError(resultado.error());
        }
    }
}
