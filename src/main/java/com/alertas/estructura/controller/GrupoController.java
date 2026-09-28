package com.alertas.estructura.controller;

import com.alertas.estructura.dto.CrearGrupoRequest;
import com.alertas.estructura.dto.GrupoResponse;
import com.alertas.estructura.dto.RenombrarGrupoRequest;
import com.alertas.estructura.service.EstructuraService;
import com.alertas.shared.idempotencia.Idempotente;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/grupos")
public class GrupoController {

    private final EstructuraService estructuraService;

    public GrupoController(EstructuraService estructuraService) {
        this.estructuraService = estructuraService;
    }

    // sin anioId trae los del anio activo
    @GetMapping
    public List<GrupoResponse> listar(@RequestParam(required = false) Long anioId) {
        return estructuraService.listarGrupos(anioId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Idempotente
    public GrupoResponse crear(@Valid @RequestBody CrearGrupoRequest request) {
        return estructuraService.crearGrupo(request.anioId(), request.gradoId(), request.nombre());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public GrupoResponse renombrar(@PathVariable Long id, @Valid @RequestBody RenombrarGrupoRequest request) {
        return estructuraService.renombrarGrupo(id, request.nombre());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void borrar(@PathVariable Long id) {
        estructuraService.borrarGrupo(id);
    }
}
