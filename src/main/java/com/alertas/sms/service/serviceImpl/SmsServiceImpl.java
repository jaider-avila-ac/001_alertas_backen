package com.alertas.sms.service.serviceImpl;

import com.alertas.shared.TenantSupport;
import com.alertas.sms.model.SmsEnvio;
import com.alertas.sms.repository.SmsEnvioRepository;
import com.alertas.sms.service.SmsDespachoService;
import com.alertas.sms.service.SmsService;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.text.Normalizer;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// textos de REQUERIMIENTOS 1.8: sin documento, sin motivo, sin categoria, sin nivel y sin tildes
@Service
public class SmsServiceImpl implements SmsService {

    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final int MAXIMO_TEXTO = 480;

    private final SmsEnvioRepository repository;
    private final SmsDespachoService despacho;
    private final EntityManager em;

    public SmsServiceImpl(SmsEnvioRepository repository, SmsDespachoService despacho, EntityManager em) {

        this.repository = repository;
        this.despacho = despacho;
        this.em = em;
    }

    @Override
    @Transactional
    public void alertaCreada(Long alertaId, Long estudianteId) {

        Long institucionId = TenantSupport.requireTenant(em);
        String colegio = colegioConSms(institucionId);
        if (colegio == null) {
            return;
        }

        Object[] estudiante = repository.estudiante(estudianteId).get(0);
        if (!Boolean.TRUE.equals(estudiante[3])) {
            // el psicorientador apago los sms a la familia de este estudiante
            return;
        }

        String nombre = estudiante[0] + " " + estudiante[1];
        String texto = "Hola, le informamos que el estudiante " + nombre + " tiene una alerta registrada en " + colegio
                + ". Orientacion escolar se comunicara con ustedes.";

        List<Long> ids = new ArrayList<>();
        for (String celular : new LinkedHashSet<>(repository.celularesFamiliares(estudianteId))) {
            ids.add(guardar(institucionId, "ALERTA_CREADA", "FAMILIAR", celular, texto, alertaId, null));
        }

        enviarAlTerminar(institucionId, ids);
    }

    @Override
    @Transactional
    public void cita(String evento, Long citaId) {

        Long institucionId = TenantSupport.requireTenant(em);
        String colegio = colegioConSms(institucionId);
        if (colegio == null) {
            return;
        }

        Object[] cita = repository.cita(citaId).get(0);
        Long estudianteId = ((Number) cita[0]).longValue();
        ZonedDateTime inicio = aZona(cita[1]);
        String dia = inicio.format(DIA);
        String hora = inicio.format(HORA);

        Object[] estudiante = repository.estudiante(estudianteId).get(0);
        String nombre = estudiante[0] + " " + estudiante[1];
        String celularEstudiante = (String) estudiante[2];
        boolean familiaActiva = Boolean.TRUE.equals(estudiante[3]);

        String paraEstudiante;
        String paraFamilia;
        if ("CITA_REPROGRAMADA".equals(evento)) {
            paraEstudiante = "Hola " + nombre + ", tu cita con orientacion escolar fue reprogramada para el " + dia
                    + " a las " + hora + " en " + colegio + ".";
            paraFamilia = "Hola, la cita con orientacion escolar del estudiante " + nombre + " fue reprogramada para el "
                    + dia + " a las " + hora + " en " + colegio + ".";
        } else if ("CITA_CANCELADA".equals(evento)) {
            paraEstudiante = "Hola " + nombre + ", tu cita con orientacion escolar del " + dia + " a las " + hora
                    + " fue cancelada. " + colegio + ".";
            paraFamilia = "Hola, la cita con orientacion escolar del estudiante " + nombre + " del " + dia + " a las "
                    + hora + " fue cancelada. " + colegio + ".";
        } else {
            paraEstudiante = "Hola " + nombre + ", tienes una cita con orientacion escolar el " + dia + " a las " + hora
                    + " en " + colegio + ".";
            paraFamilia = "Hola, el estudiante " + nombre + " tiene una cita con orientacion escolar el " + dia
                    + " a las " + hora + " en " + colegio + ".";
        }

        List<Long> ids = new ArrayList<>();
        Set<String> yaAvisados = new LinkedHashSet<>();

        if (celularEstudiante != null) {
            ids.add(guardar(institucionId, evento, "ESTUDIANTE", celularEstudiante, paraEstudiante, null, citaId));
            yaAvisados.add(celularEstudiante);
        }

        // a la familia: si no esta apagado y, en citas solo por solicitudes del estudiante, si el lo autorizo
        if (familiaActiva && repository.alertasQuePermitenFamilia(citaId) > 0) {
            for (String celular : repository.celularesFamiliares(estudianteId)) {
                if (yaAvisados.add(celular)) {
                    ids.add(guardar(institucionId, evento, "FAMILIAR", celular, paraFamilia, null, citaId));
                }
            }
        }

        enviarAlTerminar(institucionId, ids);
    }

    // ---------------------------------------------------------------- ayudas

    // null si la institucion tiene el sms apagado
    private String colegioConSms(Long institucionId) {

        Object[] institucion = repository.institucion(institucionId).get(0);

        if (!Boolean.TRUE.equals(institucion[1])) {
            return null;
        }

        return (String) institucion[0];
    }

    private Long guardar(Long institucionId, String evento, String destinatario, String celular, String texto,
            Long alertaId, Long citaId) {

        String limpio = sinTildes(texto);
        if (limpio.length() > MAXIMO_TEXTO) {
            limpio = limpio.substring(0, MAXIMO_TEXTO);
        }

        SmsEnvio sms = new SmsEnvio();
        sms.setInstitucionId(institucionId);
        sms.setEvento(evento);
        sms.setDestinatario(destinatario);
        sms.setCelular(celular);
        sms.setMensaje(limpio);
        sms.setSegmentos(segmentos(limpio));
        sms.setAlertaId(alertaId);
        sms.setCitaId(citaId);
        repository.save(sms);
        return sms.getId();
    }

    // se envian solo si la transaccion termina bien
    private void enviarAlTerminar(Long institucionId, List<Long> ids) {

        if (ids.isEmpty()) {
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                despacho.enviar(institucionId, ids);
            }
        });
    }

    // con tildes el sms se codifica distinto y cada parte baja de 160 a 70 caracteres: cuesta el doble o el triple
    static String sinTildes(String texto) {

        String separado = Normalizer.normalize(texto, Normalizer.Form.NFD);
        return separado.replaceAll("\\p{M}", "");
    }

    // 160 caracteres en uno; si es mas largo, cada parte lleva 153
    static int segmentos(String texto) {

        if (texto.length() <= 160) {
            return 1;
        }
        return (texto.length() + 152) / 153;
    }

    private ZonedDateTime aZona(Object valor) {

        Instant instante;
        if (valor instanceof OffsetDateTime) {
            instante = ((OffsetDateTime) valor).toInstant();
        } else if (valor instanceof Timestamp) {
            instante = ((Timestamp) valor).toInstant();
        } else {
            instante = (Instant) valor;
        }
        return instante.atZone(ZoneId.systemDefault());
    }
}
