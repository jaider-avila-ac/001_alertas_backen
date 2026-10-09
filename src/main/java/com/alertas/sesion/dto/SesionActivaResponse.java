package com.alertas.sesion.dto;

import com.alertas.auth.model.SesionAbierta;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

// una sesion abierta. el codigo es el de la sesion (aleatorio), no el id del usuario
public record SesionActivaResponse(
        String codigo,
        String documento,
        String nombres,
        String apellidos,
        String rol,
        String dispositivo,
        String sistema,
        String navegador,
        OffsetDateTime inicio,
        OffsetDateTime ultimaActividad,
        boolean enLinea) {

    // limite: desde cuando cuenta como en linea
    public static SesionActivaResponse desde(SesionAbierta sesion, Instant limite) {

        String nombres = sesion.nombres();
        if (nombres.isBlank()) {
            nombres = "Usuario";
        }

        return new SesionActivaResponse(
                sesion.codigo(),
                sesion.documento(),
                nombres,
                sesion.apellidos(),
                sesion.rol().name(),
                sesion.dispositivo(),
                sesion.sistema(),
                sesion.navegador(),
                fecha(sesion.inicio()),
                fecha(sesion.ultimaActividad()),
                sesion.ultimaActividad().isAfter(limite));
    }

    private static OffsetDateTime fecha(Instant instante) {
        return OffsetDateTime.ofInstant(instante, ZoneId.systemDefault());
    }
}
