package com.alertas.categoria.controller;

import com.alertas.categoria.dto.CategoriaRequest;
import com.alertas.categoria.dto.CategoriaResponse;
import com.alertas.categoria.service.CategoriaService;
import com.alertas.shared.dto.CambiarActivoRequest;
import com.alertas.shared.idempotencia.Idempotente;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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

// verlas lo puede cualquiera del colegio (el docente las necesita para crear alertas). cambiarlas solo el admin
@RestController
@RequestMapping("/api/v1/categorias")
public class CategoriaController {

    private final CategoriaService categoriaService;

    public CategoriaController(CategoriaService categoriaService) {
        this.categoriaService = categoriaService;
    }

    // estado: activas (por defecto) o todas
    @GetMapping
    public List<CategoriaResponse> listar(@RequestParam(defaultValue = "activas") String estado) {
        return categoriaService.listar(!"todas".equals(estado));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Idempotente
    public CategoriaResponse crear(@Valid @RequestBody CategoriaRequest request) {
        return categoriaService.crear(request.nombre());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public CategoriaResponse renombrar(@PathVariable Long id, @Valid @RequestBody CategoriaRequest request) {
        return categoriaService.renombrar(id, request.nombre());
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasRole('ADMIN')")
    public CategoriaResponse cambiarEstado(@PathVariable Long id, @Valid @RequestBody CambiarActivoRequest request) {
        return categoriaService.cambiarEstado(id, request.activo());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void borrar(@PathVariable Long id) {
        categoriaService.borrar(id);
    }
}
