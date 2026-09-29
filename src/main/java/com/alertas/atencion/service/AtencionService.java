package com.alertas.atencion.service;

import com.alertas.atencion.dto.BandejaResponse;
import com.alertas.atencion.dto.EstudianteAtencionResponse;
import com.alertas.atencion.dto.ExpedienteResponse;
import com.alertas.atencion.dto.PsicorientadorResponse;
import com.alertas.atencion.dto.ReasignarRequest;
import com.alertas.shared.dto.PageResponse;
import java.util.List;

// lo que hace el psicorientador con las alertas: bandeja, tomar, mis estudiantes y expediente
public interface AtencionService {

    // estudiantes con alertas pendientes que nadie ha tomado
    PageResponse<BandejaResponse> bandeja(int pagina, int tamanio);

    // le asigna todas las alertas activas del estudiante. si otro llego primero, falla
    ExpedienteResponse tomar(String estudianteCodigo);

    // pestana: POR_AGENDAR, CON_CITA, POR_CERRAR o HISTORIAL. filtros opcionales
    PageResponse<EstudianteAtencionResponse> misEstudiantes(
            String pestana, String nivel, Long categoriaId, Long gradoId, Long grupoId, int pagina, int tamanio);

    ExpedienteResponse expediente(String estudianteCodigo);

    ExpedienteResponse cambiarNivel(String alertaCodigo, String nivel);

    // una completada vuelve a quedar en proceso
    ExpedienteResponse reabrir(String alertaCodigo);

    // las alertas activas (y la cita programada) pasan a otro psicorientador. queda en la bitacora
    ExpedienteResponse reasignar(String estudianteCodigo, ReasignarRequest request);

    List<PsicorientadorResponse> psicorientadores();
}
