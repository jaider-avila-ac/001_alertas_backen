package com.alertas.estudiante.dto;

// para inactivar a los que ya no estan hay otro excel (InactivacionEstudiantesService)
public record ResultadoImportacionResponse(int creados, int actualizados) {
}
