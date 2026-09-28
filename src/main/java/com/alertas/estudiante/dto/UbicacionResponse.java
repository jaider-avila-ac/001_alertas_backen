package com.alertas.estudiante.dto;

// grupoId es de catalogo (no identifica a una persona), el front lo usa para cambiar de grupo
public record UbicacionResponse(int anio, String gradoNombre, String grupoNombre, Long grupoId) {
}
