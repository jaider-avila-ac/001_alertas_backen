package com.alertas.shared.interceptor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alertas.auth.model.Rol;
import com.alertas.auth.service.SesionService;
import com.alertas.soporte.IntegracionTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

class TenantInterceptorTest extends IntegracionTest {

    static Long colegioA;
    static Long colegioB;
    static Long colegioInactivo;
    static Long colegioSinEstudiantes;

    @Autowired
    MockMvc mvc;

    @Autowired
    SesionService sesionService;

    @BeforeAll
    static void datos() {
        colegioA = crearInstitucion("tenant-a");
        colegioB = crearInstitucion("tenant-b");
        colegioInactivo = crearInstitucion("tenant-inactivo", false, true);
        colegioSinEstudiantes = crearInstitucion("tenant-sin-est", true, false);
    }

    @Test
    void sinTokenResponde401ConMensaje() throws Exception {
        mvc.perform(get("/api/v1/prueba/tenant"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void tokenBasuraResponde401() throws Exception {
        mvc.perform(get("/api/v1/prueba/tenant").header("Authorization", "Bearer abc.def.ghi"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void elTenantSaleDelToken() throws Exception {
        mvc.perform(get("/api/v1/prueba/tenant").header("Authorization", token(1L, colegioA, "tenant-a", Rol.DOCENTE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.institucionId").value(colegioA))
                .andExpect(jsonPath("$.slug").value("tenant-a"));
    }

    @Test
    void tokenDeUnColegioEnElEnlaceDeOtroEs403() throws Exception {
        mvc.perform(get("/api/v1/prueba/tenant")
                        .header("Authorization", token(1L, colegioA, "tenant-a", Rol.ADMIN))
                        .header(TenantInterceptor.CABECERA_SLUG, "tenant-b"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Tu sesion pertenece a otra institucion"));
    }

    @Test
    void mismoSlugEnLaCabeceraPasa() throws Exception {
        mvc.perform(get("/api/v1/prueba/tenant")
                        .header("Authorization", token(1L, colegioB, "tenant-b", Rol.ADMIN))
                        .header(TenantInterceptor.CABECERA_SLUG, "tenant-b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.institucionId").value(colegioB));
    }

    @Test
    void institucionInactivaNoDejaEntrar() throws Exception {
        mvc.perform(get("/api/v1/prueba/tenant")
                        .header("Authorization", token(1L, colegioInactivo, "tenant-inactivo", Rol.ADMIN)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("La institucion no esta disponible"));
    }

    @Test
    void estudianteBloqueadoSiElAccesoEstaApagado() throws Exception {
        mvc.perform(get("/api/v1/prueba/tenant")
                        .header("Authorization", token(1L, colegioSinEstudiantes, "tenant-sin-est", Rol.ESTUDIANTE)))
                .andExpect(status().isForbidden());
        // al docente del mismo colegio no le afecta
        mvc.perform(get("/api/v1/prueba/tenant")
                        .header("Authorization", token(2L, colegioSinEstudiantes, "tenant-sin-est", Rol.DOCENTE)))
                .andExpect(status().isOk());
    }

    @Test
    void rutaPublicaUsaElSlug() throws Exception {
        mvc.perform(get("/api/v1/public/tenant-b/prueba"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.institucionId").value(colegioB));
    }

    @Test
    void rutaPublicaConSlugInexistenteOInactivoEs404() throws Exception {
        mvc.perform(get("/api/v1/public/no-existe/prueba")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/public/tenant-inactivo/prueba")).andExpect(status().isNotFound());
    }

    @Test
    void superadminNoEntraARutasDeInstitucion() throws Exception {
        mvc.perform(get("/api/v1/prueba/tenant").header("Authorization", token(1L, null, null, Rol.SUPERADMIN)))
                .andExpect(status().isForbidden());
    }

    @Test
    void usuarioDeInstitucionNoEntraAlSuperadmin() throws Exception {
        mvc.perform(get("/api/v1/superadmin/prueba").header("Authorization", token(1L, colegioA, "tenant-a", Rol.ADMIN)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/superadmin/prueba").header("Authorization", token(1L, null, null, Rol.SUPERADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.institucionId").doesNotExist());
    }

    @Test
    void despuesDeCerrarSesionesElTokenViejoNoSirve() throws Exception {
        String viejo = token(77L, colegioA, "tenant-a", Rol.DOCENTE);
        mvc.perform(get("/api/v1/prueba/tenant").header("Authorization", viejo)).andExpect(status().isOk());

        sesionService.cerrarSesiones(colegioA, 77L);
        Thread.sleep(5);

        mvc.perform(get("/api/v1/prueba/tenant").header("Authorization", viejo)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/prueba/tenant").header("Authorization", token(77L, colegioA, "tenant-a", Rol.DOCENTE)))
                .andExpect(status().isOk());
        // al mismo id de usuario en otro colegio no le afecta
        mvc.perform(get("/api/v1/prueba/tenant").header("Authorization", token(77L, colegioB, "tenant-b", Rol.DOCENTE)))
                .andExpect(status().isOk());
    }

    @Test
    void rutaQueNoExisteNoDaPistas() throws Exception {
        mvc.perform(get("/cualquier/cosa")).andExpect(status().isUnauthorized());
    }
}
