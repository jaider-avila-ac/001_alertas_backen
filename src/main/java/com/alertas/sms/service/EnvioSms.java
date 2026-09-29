package com.alertas.sms.service;

// el proveedor de sms. hoy Twilio; cambiar de proveedor es escribir otra clase que implemente esto
public interface EnvioSms {

    // celular en formato internacional (+573001234567). no lanza excepcion: devuelve el resultado
    ResultadoEnvio enviar(String celular, String texto);

    // ok: el proveedor lo acepto. proveedorId: su id del mensaje. error: por que fallo
    record ResultadoEnvio(boolean ok, String proveedorId, String error) {
    }
}
