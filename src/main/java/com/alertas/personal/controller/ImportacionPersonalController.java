package com.alertas.personal.controller;

import com.alertas.personal.dto.ResultadoImportacionPersonalResponse;
import com.alertas.personal.dto.VistaPreviaPersonalResponse;
import com.alertas.personal.service.ImportacionPersonalService;
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
@RequestMapping("/api/v1/personal")
@PreAuthorize("hasRole('ADMIN')")
public class ImportacionPersonalController {

    private static final MediaType EXCEL =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ImportacionPersonalService importacionService;

    public ImportacionPersonalController(ImportacionPersonalService importacionService) {
        this.importacionService = importacionService;
    }

    @GetMapping("/importacion/plantilla")
    public ResponseEntity<byte[]> plantilla() {
        return descargar(importacionService.plantilla(), "plantilla-personal.xlsx");
    }

    // no guarda nada: revisa el archivo y dice que pasaria
    @PostMapping(value = "/importacion/vista-previa", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public VistaPreviaPersonalResponse vistaPrevia(@RequestParam("archivo") MultipartFile archivo) {

        if (archivo.isEmpty()) {
            throw ApiException.invalido("Selecciona el archivo de Excel");
        }

        try (InputStream contenido = archivo.getInputStream()) {
            return importacionService.vistaPrevia(contenido);
        } catch (IOException e) {
            throw ApiException.invalido("No se pudo leer el archivo");
        }
    }

    @PostMapping("/importacion/{token}/confirmar")
    @Idempotente
    public ResultadoImportacionPersonalResponse confirmar(@PathVariable String token) {
        return importacionService.confirmar(token);
    }

    @GetMapping("/exportar")
    public ResponseEntity<byte[]> exportar(
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) String rol,
            @RequestParam(defaultValue = "activos") String estado) {

        String rolFiltro = null;

        if ("DOCENTE".equals(rol) || "PSICORIENTADOR".equals(rol)) {
            rolFiltro = rol;
        }

        byte[] contenido = importacionService.exportar(texto, rolFiltro, PersonalController.activoDesde(estado));
        return descargar(contenido, "personal.xlsx");
    }

    private ResponseEntity<byte[]> descargar(byte[] contenido, String nombre) {

        return ResponseEntity.ok()
                .contentType(EXCEL)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombre + "\"")
                .body(contenido);
    }
}
