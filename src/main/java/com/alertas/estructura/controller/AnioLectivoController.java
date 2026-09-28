package com.alertas.estructura.controller;

import com.alertas.estructura.dto.AnioLectivoResponse;
import com.alertas.estructura.dto.CrearAnioRequest;
import com.alertas.estructura.service.EstructuraService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/anios-lectivos")
public class AnioLectivoController {

    private final EstructuraService estructuraService;

    public AnioLectivoController(EstructuraService estructuraService) {
        this.estructuraService = estructuraService;
    }

    @GetMapping
    public List<AnioLectivoResponse> listar() {
        return estructuraService.listarAnios();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public AnioLectivoResponse crear(@Valid @RequestBody CrearAnioRequest request) {
        return estructuraService.crearAnio(request.anio());
    }

    @PatchMapping("/{id}/activar")
    @PreAuthorize("hasRole('ADMIN')")
    public AnioLectivoResponse activar(@PathVariable Long id) {
        return estructuraService.activarAnio(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void borrar(@PathVariable Long id) {
        estructuraService.borrarAnio(id);
    }
}
