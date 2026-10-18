package com.alertas.categoria.controller;

import com.alertas.categoria.dto.CategoriaResponse;
import com.alertas.categoria.service.CategoriaService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// las categorias son fijas e iguales para todas las instituciones: solo se consultan.
// las ve cualquiera del colegio (el docente y el estudiante las necesitan para crear alertas)
@RestController
@RequestMapping("/api/v1/categorias")
public class CategoriaController {

    private final CategoriaService categoriaService;

    public CategoriaController(CategoriaService categoriaService) {
        this.categoriaService = categoriaService;
    }

    @GetMapping
    public List<CategoriaResponse> listar() {
        return categoriaService.listar();
    }
}
