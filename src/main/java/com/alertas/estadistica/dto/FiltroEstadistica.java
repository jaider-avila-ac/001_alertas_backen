package com.alertas.estadistica.dto;

import java.time.LocalDate;

// todo opcional: null = sin ese filtro. anio es el del calendario. desde y hasta incluyen el dia.
// edad es la clave de RangoEdad
public record FiltroEstadistica(
        Integer anio,
        LocalDate desde,
        LocalDate hasta,
        String edad,
        Long categoriaId) {
}
