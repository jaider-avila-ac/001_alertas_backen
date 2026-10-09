package com.alertas.auth.model;

import java.time.Instant;

// una sesion iniciada que sigue abierta (guardada en redis mientras dura el token).
// el nombre y el documento se guardan al entrar para no ir a la bd en cada aviso
public record SesionAbierta(
        String codigo,
        Long institucionId,
        Long usuarioId,
        Rol rol,
        Instant inicio,
        Instant ultimaActividad,
        String dispositivo,
        String sistema,
        String navegador,
        String documento,
        String nombres,
        String apellidos) {
}
