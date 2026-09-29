package com.alertas.sms.service;

// arma los sms segun las reglas (a quien, que texto) y los deja pendientes; se envian cuando la transaccion termina bien.
// si la institucion tiene el sms apagado no se hace nada
public interface SmsService {

    // un docente creo una alerta: a los familiares (el estudiante estaba presente)
    void alertaCreada(Long alertaId, Long estudianteId);

    // evento: CITA_AGENDADA, CITA_REPROGRAMADA o CITA_CANCELADA. al estudiante y a los familiares
    void cita(String evento, Long citaId);
}
