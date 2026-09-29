package com.alertas.estadistica.dto;

import java.time.LocalDate;

// todo opcional: null = sin ese filtro. desde y hasta incluyen el dia
public record FiltroEstadistica(
        Long anioId,
        LocalDate desde,
        LocalDate hasta,
        Long gradoId,
        Long grupoId,
        Long categoriaId) {
}
