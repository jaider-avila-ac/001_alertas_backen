package com.alertas.estadistica.dto;

import java.time.LocalDate;

// todo opcional: null = sin ese filtro. desde y hasta incluyen el dia.
// anioId null = el anio activo; todosLosAnios = sin filtro de anio
public record FiltroEstadistica(
        Long anioId,
        boolean todosLosAnios,
        LocalDate desde,
        LocalDate hasta,
        Long gradoId,
        Long grupoId,
        Long categoriaId) {
}
