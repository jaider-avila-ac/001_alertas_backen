package com.alertas.atencion.dto;

import com.alertas.cita.dto.CitaResponse;
import com.alertas.valoracion.dto.ValoracionResponse;
import java.util.List;

// todo lo del estudiante para el psicorientador. psicorientador: quien atiende sus alertas activas (null si nadie).
// esMio: lo atiende el psicorientador de la sesion. sinTomar: tiene alertas activas que nadie ha tomado
public record ExpedienteResponse(
        String estudianteCodigo,
        String nombres,
        String apellidos,
        String gradoNombre,
        String grupoNombre,
        boolean activo,
        String psicorientador,
        boolean esMio,
        boolean sinTomar,
        String citaProgramada,
        List<AlertaExpedienteResponse> alertas,
        List<CitaResponse> citas,
        List<ValoracionResponse> valoraciones) {
}
