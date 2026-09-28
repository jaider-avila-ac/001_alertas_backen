package com.alertas.estudiante.service;

import com.alertas.estudiante.dto.EstadoMasivoEstudiantesRequest;
import com.alertas.estudiante.dto.EstudianteDetalleResponse;
import com.alertas.estudiante.dto.EstudianteFilaResponse;
import com.alertas.estudiante.dto.EstudianteRequest;
import com.alertas.estudiante.dto.FamiliarRequest;
import com.alertas.estudiante.dto.QrResponse;
import com.alertas.shared.dto.NombrePersona;
import com.alertas.shared.dto.PageResponse;
import java.util.List;

// estudiantes de la institucion del contexto. afuera siempre se identifican por su codigo
public interface EstudianteService {

    // filtros opcionales (null = sin filtro). grado y grupo son del anio activo
    PageResponse<EstudianteFilaResponse> listar(
            String texto, Long gradoId, Long grupoId, Boolean activo, boolean sinGrupo, int pagina, int tamanio);

    EstudianteDetalleResponse buscar(String codigo);

    EstudianteDetalleResponse crear(EstudianteRequest request);

    EstudianteDetalleResponse actualizar(String codigo, EstudianteRequest request);

    // matricula en el anio del grupo o lo mueve de grupo (queda el movimiento). si estaba retirado vuelve
    EstudianteDetalleResponse cambiarGrupo(String codigo, Long grupoId, String motivo);

    // cierra sus matriculas activas como retiradas e inactiva su usuario
    EstudianteDetalleResponse retirar(String codigo, String motivo);

    EstudianteDetalleResponse guardarFamiliares(String codigo, List<FamiliarRequest> familiares);

    EstudianteDetalleResponse cambiarSmsFamiliares(String codigo, boolean activo);

    // ---- usuario del estudiante ----

    EstudianteDetalleResponse restablecerContrasena(String codigo);

    EstudianteDetalleResponse cambiarEstado(String codigo, boolean activo);

    int cambiarEstadoMasivo(EstadoMasivoEstudiantesRequest request);

    // al abrir un anio nuevo: los que no tienen matricula en ese anio (se retiraron o se graduaron)
    long contarSinGrupo(Long anioId);

    int inactivarSinGrupo(Long anioId);

    // ---- qr ----

    QrResponse verQr(String codigo);

    // el qr viejo deja de funcionar
    QrResponse regenerarQr(String codigo);

    List<QrResponse> qrDelGrupo(Long grupoId);

    // el docente escanea el qr: devuelve el estudiante para crear la alerta
    EstudianteFilaResponse buscarPorQr(String codigoQr);

    // ---- para otros modulos ----

    // null si ese usuario no es estudiante
    NombrePersona buscarNombre(Long usuarioId);
}
