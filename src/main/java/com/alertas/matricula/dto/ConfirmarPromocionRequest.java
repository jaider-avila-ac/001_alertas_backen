package com.alertas.matricula.dto;

import jakarta.validation.Valid;
import java.util.List;

// sin asignaciones se usa el destino sugerido de cada grupo
public record ConfirmarPromocionRequest(@Valid List<AsignacionGrupo> asignaciones) {
}
