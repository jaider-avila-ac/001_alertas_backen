package com.alertas.institucion.controller;

import com.alertas.institucion.dto.AsignarContrasenaRequest;
import com.alertas.institucion.dto.CrearInstitucionRequest;
import com.alertas.institucion.dto.CrearInstitucionResponse;
import com.alertas.institucion.dto.InactivarInstitucionRequest;
import com.alertas.institucion.dto.InstitucionDatosRequest;
import com.alertas.institucion.dto.InstitucionResponse;
import com.alertas.institucion.service.InstitucionService;
import com.alertas.personal.dto.AdministradorRequest;
import com.alertas.personal.dto.AdministradorResponse;
import com.alertas.shared.dto.CambiarActivoRequest;
import com.alertas.shared.dto.PageResponse;
import com.alertas.shared.idempotencia.Idempotente;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
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

// todo esto es del superadmin (SecurityConfig pide el rol en /api/v1/superadmin/**)
@RestController
@RequestMapping("/api/v1/superadmin/instituciones")
public class InstitucionController {

    private final InstitucionService institucionService;

    public InstitucionController(InstitucionService institucionService) {
        this.institucionService = institucionService;
    }

    @GetMapping
    public PageResponse<InstitucionResponse> listar(
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) Boolean activa,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanio) {

        return institucionService.listar(texto, activa, pagina, tamanio);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Idempotente
    public CrearInstitucionResponse crear(@Valid @RequestBody CrearInstitucionRequest request) {
        return institucionService.crear(request);
    }

    @GetMapping("/{id}")
    public InstitucionResponse buscar(@PathVariable Long id) {
        return institucionService.buscar(id);
    }

    @PutMapping("/{id}")
    public InstitucionResponse actualizar(@PathVariable Long id, @Valid @RequestBody InstitucionDatosRequest request) {
        return institucionService.actualizar(id, request);
    }

    @PatchMapping("/{id}/inactivar")
    public InstitucionResponse inactivar(@PathVariable Long id, @Valid @RequestBody InactivarInstitucionRequest request) {
        return institucionService.inactivar(id, request.motivo());
    }

    @PatchMapping("/{id}/activar")
    public InstitucionResponse activar(@PathVariable Long id) {
        return institucionService.activar(id);
    }

    @PatchMapping("/{id}/sms")
    public InstitucionResponse cambiarSms(@PathVariable Long id, @Valid @RequestBody CambiarActivoRequest request) {
        return institucionService.cambiarSms(id, request.activo());
    }

    // ---- administradores de la institucion ----

    @GetMapping("/{id}/administradores")
    public List<AdministradorResponse> listarAdministradores(@PathVariable Long id) {
        return institucionService.listarAdministradores(id);
    }

    @PostMapping("/{id}/administradores")
    @ResponseStatus(HttpStatus.CREATED)
    @Idempotente
    public AdministradorResponse crearAdministrador(@PathVariable Long id, @Valid @RequestBody AdministradorRequest request) {
        return institucionService.crearAdministrador(id, request);
    }

    @PostMapping("/{id}/administradores/{usuarioId}/restablecer-contrasena")
    public AdministradorResponse restablecerContrasena(@PathVariable Long id, @PathVariable Long usuarioId) {
        return institucionService.restablecerContrasenaAdministrador(id, usuarioId);
    }

    // el administrador no puede cambiar su propia contrasena, se la asigna el superadmin
    @PutMapping("/{id}/administradores/{usuarioId}/contrasena")
    public AdministradorResponse asignarContrasena(
            @PathVariable Long id,
            @PathVariable Long usuarioId,
            @Valid @RequestBody AsignarContrasenaRequest request) {

        return institucionService.asignarContrasenaAdministrador(id, usuarioId, request.nueva());
    }

    @PatchMapping("/{id}/administradores/{usuarioId}/estado")
    public AdministradorResponse cambiarEstadoAdministrador(
            @PathVariable Long id,
            @PathVariable Long usuarioId,
            @Valid @RequestBody CambiarActivoRequest request) {

        return institucionService.cambiarEstadoAdministrador(id, usuarioId, request.activo());
    }
}
