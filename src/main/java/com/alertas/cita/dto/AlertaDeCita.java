package com.alertas.cita.dto;

// resultado y observacion son null hasta que se finaliza la cita. la observacion solo va a psicorientadores
public record AlertaDeCita(
        String codigo,
        String categoria,
        String nivel,
        String descripcion,
        String resultado,
        String observacion) {
}
