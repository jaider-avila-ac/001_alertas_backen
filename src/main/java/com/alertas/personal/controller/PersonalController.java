package com.alertas.personal.controller;

import com.alertas.personal.dto.ActualizarPersonalRequest;
import com.alertas.personal.dto.EstadoMasivoPersonalRequest;
import com.alertas.personal.dto.PersonalDetalleResponse;
import com.alertas.personal.dto.PersonalFilaResponse;
import com.alertas.personal.dto.PersonalRequest;
import com.alertas.personal.service.PersonalService;
import com.alertas.shared.dto.CambiarActivoRequest;
import com.alertas.shared.dto.PageResponse;
import com.alertas.shared.idempotencia.Idempotente;
import com.alertas.usuario.dto.EstadoMasivoResponse;
import jakarta.validation.Valid;
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

// docentes y psicorientadores, por su codigo. solo el admin del colegio
@RestController
@RequestMapping("/api/v1/personal")
@PreAuthorize("hasRole('ADMIN')")
public class PersonalController {

    private final PersonalService personalService;

    public PersonalController(PersonalService personalService) {
        this.personalService = personalService;
    }

    // rol: DOCENTE o PSICORIENTADOR (vacio = los dos). estado: activos, inactivos o todos
    @GetMapping
    public PageResponse<PersonalFilaResponse> listar(
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) String rol,
            @RequestParam(defaultValue = "activos") String estado,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanio) {

        String rolFiltro = null;

        if ("DOCENTE".equals(rol) || "PSICORIENTADOR".equals(rol)) {
            rolFiltro = rol;
        }

        return personalService.listar(texto, rolFiltro, activoDesde(estado), pagina, tamanio);
    }

    @GetMapping("/{codigo}")
    public PersonalDetalleResponse buscar(@PathVariable String codigo) {
        return personalService.buscar(codigo);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Idempotente
    public PersonalDetalleResponse crear(@Valid @RequestBody PersonalRequest request) {
        return personalService.crear(request);
    }

    @PutMapping("/{codigo}")
    public PersonalDetalleResponse actualizar(@PathVariable String codigo, @Valid @RequestBody ActualizarPersonalRequest request) {
        return personalService.actualizar(codigo, request);
    }

    @PostMapping("/{codigo}/restablecer-contrasena")
    public PersonalDetalleResponse restablecerContrasena(@PathVariable String codigo) {
        return personalService.restablecerContrasena(codigo);
    }

    @PatchMapping("/{codigo}/estado")
    public PersonalDetalleResponse cambiarEstado(@PathVariable String codigo, @Valid @RequestBody CambiarActivoRequest request) {
        return personalService.cambiarEstado(codigo, request.activo());
    }

    @PatchMapping("/estado-masivo")
    public EstadoMasivoResponse cambiarEstadoMasivo(@Valid @RequestBody EstadoMasivoPersonalRequest request) {

        int afectados = personalService.cambiarEstadoMasivo(request);
        return new EstadoMasivoResponse(afectados);
    }

    static Boolean activoDesde(String estado) {

        if ("activos".equals(estado)) {
            return true;
        }

        if ("inactivos".equals(estado)) {
            return false;
        }

        return null;
    }
}
