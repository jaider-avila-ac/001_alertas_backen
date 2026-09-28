package com.alertas.soporte;

import com.alertas.shared.exception.ApiException;
import com.alertas.shared.idempotencia.Idempotente;
import com.alertas.shared.interceptor.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

// endpoints de mentira solo para probar las piezas base
@RestController
public class PruebaController {

    public record TenantVisto(Long institucionId, String slug) {
    }

    public record Dato(@NotBlank(message = "El nombre es obligatorio") String nombre) {
    }

    @GetMapping("/api/v1/prueba/tenant")
    public TenantVisto tenant() {
        return visto();
    }

    @GetMapping("/api/v1/public/{slug}/prueba")
    public TenantVisto publica() {
        return visto();
    }

    @GetMapping("/api/v1/superadmin/prueba")
    public TenantVisto superadmin() {
        return visto();
    }

    @PostMapping("/api/v1/prueba/idempotente")
    @Idempotente
    public String idempotente() {
        return "ok";
    }

    @PostMapping("/api/v1/prueba/idempotente-falla")
    @Idempotente
    public String idempotenteFalla() {
        throw ApiException.invalido("fallo a proposito");
    }

    @PostMapping("/api/v1/prueba/validacion")
    public String validacion(@Valid @RequestBody Dato dato) {
        return dato.nombre();
    }

    private TenantVisto visto() {
        return new TenantVisto(TenantContext.getInstitucionId(), TenantContext.getSlug());
    }
}
