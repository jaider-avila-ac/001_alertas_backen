package com.alertas.estudiante.dto;

import java.util.List;

public record ImportacionGuardada(Long anioId, List<FilaImportada> filas) {
}
