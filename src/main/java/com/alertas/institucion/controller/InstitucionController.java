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

    @GetMapping("/{slug}")
    public InstitucionResponse buscar(@PathVariable String slug) {
        return institucionService.buscar(slug);
    }

    @PutMapping("/{slug}")
    public InstitucionResponse actualizar(@PathVariable String slug, @Valid @RequestBody InstitucionDatosRequest request) {
        return institucionService.actualizar(slug, request);
    }

    @PatchMapping("/{slug}/inactivar")
    public InstitucionResponse inactivar(@PathVariable String slug, @Valid @RequestBody InactivarInstitucionRequest request) {
        return institucionService.inactivar(slug, request.motivo());
    }

    @PatchMapping("/{slug}/activar")
    public InstitucionResponse activar(@PathVariable String slug) {
        return institucionService.activar(slug);
    }

    @PatchMapping("/{slug}/sms")
    public InstitucionResponse cambiarSms(@PathVariable String slug, @Valid @RequestBody CambiarActivoRequest request) {
        return institucionService.cambiarSms(slug, request.activo());
    }

    // ---- administradores de la institucion ----

    @GetMapping("/{slug}/administradores")
    public List<AdministradorResponse> listarAdministradores(@PathVariable String slug) {
        return institucionService.listarAdministradores(slug);
    }

    @PostMapping("/{slug}/administradores")
    @ResponseStatus(HttpStatus.CREATED)
    @Idempotente
    public AdministradorResponse crearAdministrador(@PathVariable String slug, @Valid @RequestBody AdministradorRequest request) {
        return institucionService.crearAdministrador(slug, request);
    }

    @PutMapping("/{slug}/administradores/{codigo}")
    public AdministradorResponse actualizarAdministrador(
            @PathVariable String slug,
            @PathVariable String codigo,
            @Valid @RequestBody AdministradorRequest request) {

        return institucionService.actualizarAdministrador(slug, codigo, request);
    }

    @PostMapping("/{slug}/administradores/{codigo}/restablecer-contrasena")
    public AdministradorResponse restablecerContrasena(@PathVariable String slug, @PathVariable String codigo) {
        return institucionService.restablecerContrasenaAdministrador(slug, codigo);
    }

    // el administrador no puede cambiar su propia contrasena, se la asigna el superadmin
    @PutMapping("/{slug}/administradores/{codigo}/contrasena")
    public AdministradorResponse asignarContrasena(
            @PathVariable String slug,
            @PathVariable String codigo,
            @Valid @RequestBody AsignarContrasenaRequest request) {

        return institucionService.asignarContrasenaAdministrador(slug, codigo, request.nueva());
    }

    @PatchMapping("/{slug}/administradores/{codigo}/estado")
    public AdministradorResponse cambiarEstadoAdministrador(
            @PathVariable String slug,
            @PathVariable String codigo,
            @Valid @RequestBody CambiarActivoRequest request) {

        return institucionService.cambiarEstadoAdministrador(slug, codigo, request.activo());
    }
}
