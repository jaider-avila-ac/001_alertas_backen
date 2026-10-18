package com.alertas.estadistica.dto;

import java.util.List;

// lo que el front necesita para armar los filtros: llega en la misma respuesta de las estadisticas
public record FiltrosDisponiblesResponse(
        List<Integer> anios,
        List<String> grupos,
        List<Opcion> edades,
        List<Categoria> categorias) {

    public record Opcion(String clave, String nombre) {
    }

    public record Categoria(Long id, String nombre) {
    }
}
