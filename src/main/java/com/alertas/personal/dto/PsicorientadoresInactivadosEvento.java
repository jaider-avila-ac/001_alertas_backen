package com.alertas.personal.dto;

// se inactivo al menos un psicorientador. el modulo de atencion devuelve sus casos abiertos a la bandeja.
// es un evento para que personal no dependa de atencion (atencion ya depende de personal)
public record PsicorientadoresInactivadosEvento(Long institucionId) {
}
