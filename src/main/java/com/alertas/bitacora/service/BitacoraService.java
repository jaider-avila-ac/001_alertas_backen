package com.alertas.bitacora.service;

// registro de acciones sensibles en la institucion del contexto.
// el autor sale del token (usuario o superadmin)
public interface BitacoraService {

    void registrar(String accion, String entidad, Long entidadId, String detalle);
}
