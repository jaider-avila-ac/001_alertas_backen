package com.alertas.estudiante.controller;

import com.alertas.estudiante.dto.ResultadoInactivacionResponse;
import com.alertas.estudiante.dto.VistaPreviaInactivacionResponse;
import com.alertas.estudiante.service.InactivacionEstudiantesService;
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

// inactivar en bloque a los estudiantes que ya no estan, con un excel de documentos
@RestController
@RequestMapping("/api/v1/estudiantes/inactivacion")
@PreAuthorize("hasRole('ADMIN')")
public class InactivacionEstudiantesController {

    private static final MediaType EXCEL =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final InactivacionEstudiantesService inactivacionService;

    public InactivacionEstudiantesController(InactivacionEstudiantesService inactivacionService) {
        this.inactivacionService = inactivacionService;
    }

    @GetMapping("/plantilla")
    public ResponseEntity<byte[]> plantilla() {

        return ResponseEntity.ok()
                .contentType(EXCEL)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"plantilla-inactivar-estudiantes.xlsx\"")
                .body(inactivacionService.plantilla());
    }

    // no cambia nada: dice a quienes se inactivaria
    @PostMapping(value = "/vista-previa", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public VistaPreviaInactivacionResponse vistaPrevia(@RequestParam("archivo") MultipartFile archivo) {

        if (archivo.isEmpty()) {
            throw ApiException.invalido("Selecciona el archivo de Excel");
        }

        try (InputStream contenido = archivo.getInputStream()) {
            return inactivacionService.vistaPrevia(contenido);
        } catch (IOException e) {
            throw ApiException.invalido("No se pudo leer el archivo");
        }
    }

    @PostMapping("/{token}/confirmar")
    @Idempotente
    public ResultadoInactivacionResponse confirmar(@PathVariable String token) {
        return inactivacionService.confirmar(token);
    }
}
