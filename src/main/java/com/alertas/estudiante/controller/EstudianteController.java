package com.alertas.estudiante.controller;

import com.alertas.estudiante.dto.EstadoMasivoEstudiantesRequest;
import com.alertas.estudiante.dto.EstudianteDetalleResponse;
import com.alertas.estudiante.dto.EstudianteFilaResponse;
import com.alertas.estudiante.dto.EstudianteRequest;
import com.alertas.estudiante.dto.FamiliaresRequest;
import com.alertas.estudiante.dto.InactivarSinGrupoRequest;
import com.alertas.estudiante.dto.QrResponse;
import com.alertas.estudiante.dto.TotalResponse;
import com.alertas.estudiante.service.EstudianteService;
import com.alertas.matricula.dto.RetirarRequest;
import com.alertas.matricula.dto.UbicarRequest;
import com.alertas.shared.dto.CambiarActivoRequest;
import com.alertas.shared.dto.PageResponse;
import com.alertas.shared.idempotencia.Idempotente;
import com.alertas.usuario.dto.EstadoMasivoResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// los estudiantes se identifican por su codigo, nunca por el id
@RestController
@RequestMapping("/api/v1/estudiantes")
public class EstudianteController {

    private final EstudianteService estudianteService;

    public EstudianteController(EstudianteService estudianteService) {
        this.estudianteService = estudianteService;
    }

    // el docente tambien busca estudiantes (para crear alertas). estado: activos, inactivos o todos
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'PSICORIENTADOR', 'DOCENTE')")
    public PageResponse<EstudianteFilaResponse> listar(
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) Long gradoId,
            @RequestParam(required = false) Long grupoId,
            @RequestParam(defaultValue = "activos") String estado,
            @RequestParam(defaultValue = "false") boolean sinGrupo,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanio) {

        Boolean activo = null;

        if (estado.equals("activos")) {
            activo = true;
        } else if (estado.equals("inactivos")) {
            activo = false;
        }

        return estudianteService.listar(texto, gradoId, grupoId, activo, sinGrupo, pagina, tamanio);
    }

    // el detalle tiene datos de la familia, el docente no lo ve
    @GetMapping("/{codigo}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PSICORIENTADOR')")
    public EstudianteDetalleResponse buscar(@PathVariable String codigo) {
        return estudianteService.buscar(codigo);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Idempotente
    public EstudianteDetalleResponse crear(@Valid @RequestBody EstudianteRequest request) {
        return estudianteService.crear(request);
    }

    @PutMapping("/{codigo}")
    @PreAuthorize("hasRole('ADMIN')")
    public EstudianteDetalleResponse actualizar(@PathVariable String codigo, @Valid @RequestBody EstudianteRequest request) {
        return estudianteService.actualizar(codigo, request);
    }

    // matricular o cambiar de grupo (tambien de grado). queda el movimiento en su trayectoria
    @PatchMapping("/{codigo}/grupo")
    @PreAuthorize("hasRole('ADMIN')")
    public EstudianteDetalleResponse cambiarGrupo(@PathVariable String codigo, @Valid @RequestBody UbicarRequest request) {
        return estudianteService.cambiarGrupo(codigo, request.grupoId(), request.motivo());
    }

    @PostMapping("/{codigo}/retirar")
    @PreAuthorize("hasRole('ADMIN')")
    public EstudianteDetalleResponse retirar(@PathVariable String codigo, @Valid @RequestBody RetirarRequest request) {
        return estudianteService.retirar(codigo, request.motivo());
    }

    @PutMapping("/{codigo}/familiares")
    @PreAuthorize("hasAnyRole('ADMIN', 'PSICORIENTADOR')")
    public EstudianteDetalleResponse guardarFamiliares(
            @PathVariable String codigo,
            @Valid @RequestBody FamiliaresRequest request) {

        return estudianteService.guardarFamiliares(codigo, request.familiares());
    }

    @PatchMapping("/{codigo}/sms-familiares")
    @PreAuthorize("hasAnyRole('ADMIN', 'PSICORIENTADOR')")
    public EstudianteDetalleResponse cambiarSmsFamiliares(
            @PathVariable String codigo,
            @Valid @RequestBody CambiarActivoRequest request) {

        return estudianteService.cambiarSmsFamiliares(codigo, request.activo());
    }

    // ---- usuario del estudiante ----

    @PostMapping("/{codigo}/restablecer-contrasena")
    @PreAuthorize("hasRole('ADMIN')")
    public EstudianteDetalleResponse restablecerContrasena(@PathVariable String codigo) {
        return estudianteService.restablecerContrasena(codigo);
    }

    @PatchMapping("/{codigo}/estado")
    @PreAuthorize("hasRole('ADMIN')")
    public EstudianteDetalleResponse cambiarEstado(@PathVariable String codigo, @Valid @RequestBody CambiarActivoRequest request) {
        return estudianteService.cambiarEstado(codigo, request.activo());
    }

    @PatchMapping("/estado-masivo")
    @PreAuthorize("hasRole('ADMIN')")
    public EstadoMasivoResponse cambiarEstadoMasivo(@Valid @RequestBody EstadoMasivoEstudiantesRequest request) {

        int afectados = estudianteService.cambiarEstadoMasivo(request);
        return new EstadoMasivoResponse(afectados);
    }

    @GetMapping("/sin-grupo/total")
    @PreAuthorize("hasRole('ADMIN')")
    public TotalResponse contarSinGrupo(@RequestParam Long anioId) {
        return new TotalResponse(estudianteService.contarSinGrupo(anioId));
    }

    @PostMapping("/sin-grupo/inactivar")
    @PreAuthorize("hasRole('ADMIN')")
    public EstadoMasivoResponse inactivarSinGrupo(@Valid @RequestBody InactivarSinGrupoRequest request) {

        int afectados = estudianteService.inactivarSinGrupo(request.anioId());
        return new EstadoMasivoResponse(afectados);
    }

    // ---- qr ----

    @GetMapping("/{codigo}/qr")
    @PreAuthorize("hasRole('ADMIN')")
    public QrResponse verQr(@PathVariable String codigo) {
        return estudianteService.verQr(codigo);
    }

    @PostMapping("/{codigo}/qr/regenerar")
    @PreAuthorize("hasRole('ADMIN')")
    public QrResponse regenerarQr(@PathVariable String codigo) {
        return estudianteService.regenerarQr(codigo);
    }

    @GetMapping("/qr")
    @PreAuthorize("hasRole('ADMIN')")
    public List<QrResponse> qrDelGrupo(@RequestParam Long grupoId) {
        return estudianteService.qrDelGrupo(grupoId);
    }

    // lo que pasa cuando el docente escanea el qr de un estudiante
    @GetMapping("/por-qr/{codigoQr}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PSICORIENTADOR', 'DOCENTE')")
    public EstudianteFilaResponse buscarPorQr(@PathVariable String codigoQr) {
        return estudianteService.buscarPorQr(codigoQr);
    }
}
