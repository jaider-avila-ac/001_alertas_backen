package com.alertas.sms.service;

import java.util.List;

// envia en segundo plano los sms que quedaron pendientes. si el proveedor falla, la accion del usuario no se entera
public interface SmsDespachoService {

    void enviar(Long institucionId, List<Long> smsIds);
}
