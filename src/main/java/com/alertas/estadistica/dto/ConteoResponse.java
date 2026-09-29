package com.alertas.estadistica.dto;

// clave: el valor que filtra (ej. ALTO, 2026-03). etiqueta: lo que se muestra (ej. Alto, mar 2026)
public record ConteoResponse(String clave, String etiqueta, long total) {
}
