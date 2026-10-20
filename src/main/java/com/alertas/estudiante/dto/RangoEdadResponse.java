package com.alertas.estudiante.dto;

// un rango de edad con cuantos estudiantes activos tiene (solo salen los que tienen)
public record RangoEdadResponse(String clave, String texto, long total) {
}
