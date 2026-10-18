package com.alertas.estudiante.dto;

import java.util.List;

// lo que queda en redis entre la vista previa y la confirmacion
public record ImportacionGuardada(List<FilaImportada> filas) {
}
