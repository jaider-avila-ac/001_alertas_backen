package com.alertas.estudiante.controller;

import com.alertas.estudiante.dto.ResultadoImportacionResponse;
import com.alertas.estudiante.dto.VistaPreviaImportacionResponse;
import com.alertas.estudiante.service.ImportacionEstudiantesService;
import com.alertas.shared.exception.ApiException;
import com.alertas.shared.idempotencia.Idempotente;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/estudiantes")
@PreAuthorize("hasRole('ADMIN')")
public class ImportacionEstudiantesController {

    private static final MediaType EXCEL =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ImportacionEstudiantesService importacionService;

    public ImportacionEstudiantesController(ImportacionEstudiantesService importacionService) {
        this.importacionService = importacionService;
    }

    @GetMapping("/importacion/plantilla")
    public ResponseEntity<byte[]> plantilla() {
        return descargar(importacionService.plantilla(), "plantilla-estudiantes.xlsx");
    }

    // no guarda nada: revisa el archivo y dice que pasaria
    @PostMapping(value = "/importacion/vista-previa", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public VistaPreviaImportacionResponse vistaPrevia(
            @RequestParam("archivo") MultipartFile archivo,
            @RequestParam(required = false) Long anioId) {

        if (archivo.isEmpty()) {
            throw ApiException.invalido("Selecciona el archivo de Excel");
        }

        try (InputStream contenido = archivo.getInputStream()) {
            return importacionService.vistaPrevia(contenido, anioId);
        } catch (IOException e) {
            throw ApiException.invalido("No se pudo leer el archivo");
        }
    }

    @PostMapping("/importacion/{token}/confirmar")
    @Idempotente
    public ResultadoImportacionResponse confirmar(@PathVariable String token) {
        return importacionService.confirmar(token);
    }

    @GetMapping("/exportar")
    public ResponseEntity<byte[]> exportar(
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) Long gradoId,
            @RequestParam(required = false) Long grupoId,
            @RequestParam(defaultValue = "activos") String estado) {

        Boolean activo = null;

        if (estado.equals("activos")) {
            activo = true;
        } else if (estado.equals("inactivos")) {
            activo = false;
        }

        return descargar(importacionService.exportar(texto, gradoId, grupoId, activo), "estudiantes.xlsx");
    }

    private ResponseEntity<byte[]> descargar(byte[] contenido, String nombre) {

        return ResponseEntity.ok()
                .contentType(EXCEL)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombre + "\"")
                .body(contenido);
    }
}
