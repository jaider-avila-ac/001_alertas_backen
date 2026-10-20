package com.alertas.estudiante.service;

import com.alertas.estudiante.dto.EstadoMasivoEstudiantesRequest;
import com.alertas.estudiante.dto.EstudianteBasico;
import com.alertas.estudiante.dto.EstudianteDetalleResponse;
import com.alertas.estudiante.dto.EstudianteFilaResponse;
import com.alertas.estudiante.dto.EstudianteRequest;
import com.alertas.estudiante.dto.FamiliarRequest;
import com.alertas.estudiante.dto.QrResponse;
import com.alertas.estudiante.dto.RangoEdadResponse;
import com.alertas.shared.dto.NombrePersona;
import com.alertas.shared.dto.PageResponse;
import java.util.List;

// estudiantes de la institucion del contexto. afuera siempre se identifican por su codigo
public interface EstudianteService {

    // filtros opcionales (null = sin filtro). grupo es el texto libre; edad es la clave de RangoEdad
    PageResponse<EstudianteFilaResponse> listar(
            String texto, String grupo, String edad, Boolean activo, int pagina, int tamanio);

    // los grupos que se han escrito, para los filtros
    List<String> grupos();

    EstudianteDetalleResponse buscar(String codigo);

    EstudianteDetalleResponse crear(EstudianteRequest request);

    EstudianteDetalleResponse actualizar(String codigo, EstudianteRequest request);

    EstudianteDetalleResponse guardarFamiliares(String codigo, List<FamiliarRequest> familiares);

    EstudianteDetalleResponse cambiarSmsFamiliares(String codigo, boolean activo);

    // ---- usuario del estudiante ----

    EstudianteDetalleResponse restablecerContrasena(String codigo);

    // inactivar = ya no esta en la institucion (se retiro o se graduo)
    EstudianteDetalleResponse cambiarEstado(String codigo, boolean activo);

    int cambiarEstadoMasivo(EstadoMasivoEstudiantesRequest request);

    // ---- qr ----

    QrResponse verQr(String codigo);

    // el qr viejo deja de funcionar
    QrResponse regenerarQr(String codigo);

    // qr de los activos para imprimir, por paginas; edad null = todos los rangos
    PageResponse<QrResponse> qrParaImprimir(String edad, int pagina);

    // los rangos de edad que tienen estudiantes activos, en orden
    List<RangoEdadResponse> rangosConEstudiantes();

    // el docente escanea el qr: devuelve el estudiante para crear la alerta
    EstudianteFilaResponse buscarPorQr(String codigoQr);

    // ---- para otros modulos ----

    // null si ese usuario no es estudiante
    NombrePersona buscarNombre(Long usuarioId);

    // falla con 404 si no existe en la institucion
    EstudianteBasico basicoPorCodigo(String codigo);

    // null si ese usuario no es estudiante
    EstudianteBasico basicoPorUsuario(Long usuarioId);

    // uso interno: el id viene de otra tabla (una alerta, una cita). falla con 404 si no existe
    EstudianteBasico basicoPorId(Long estudianteId);
}
