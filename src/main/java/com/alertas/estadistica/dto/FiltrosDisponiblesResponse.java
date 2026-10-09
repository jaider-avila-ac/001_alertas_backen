package com.alertas.estadistica.dto;

import java.util.List;

// lo que el front necesita para armar los filtros: llega en la misma respuesta de las estadisticas.
// los grupos son los del anio que se esta viendo (o del activo si se ven todos los anios)
public record FiltrosDisponiblesResponse(
        List<Anio> anios,
        List<Opcion> grados,
        List<Grupo> grupos,
        List<Opcion> categorias) {

    public record Anio(Long id, int anio, boolean activo) {
    }

    public record Opcion(Long id, String nombre) {
    }

    public record Grupo(Long id, String nombre, Long gradoId) {
    }
}
