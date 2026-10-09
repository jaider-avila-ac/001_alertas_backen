package com.alertas.sesion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alertas.auth.model.Rol;
import com.alertas.soporte.IntegracionTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class SesionActivaTest extends IntegracionTest {

    static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    static final String CELULAR = "Mozilla/5.0 (Linux; Android 14; SM-A546E) AppleWebKit/537.36 (KHTML, like Gecko) "
            + "Chrome/129.0.0.0 Mobile Safari/537.36";
    static final String COMPUTADOR = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:131.0) Gecko/20100101 Firefox/131.0";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @BeforeAll
    static void datos() {

        Long colegioA = crearInstitucion("sesiones-a");
        Long colegioB = crearInstitucion("sesiones-b");
        Long colegioC = crearInstitucion("sesiones-c");

        crearUsuario(colegioA, "7100", Rol.DOCENTE, ENCODER.encode("7100"), false);
        crearUsuario(colegioA, "7200", Rol.ESTUDIANTE, ENCODER.encode("7200"), false);
        crearUsuario(colegioB, "7300", Rol.DOCENTE, ENCODER.encode("7300"), false);
        crearUsuario(colegioC, "7400", Rol.DOCENTE, ENCODER.encode("7400"), false);
    }

    private String entrar(String slug, String usuario, String navegador) throws Exception {

        String body = "{\"usuario\":\"" + usuario + "\",\"contrasena\":\"" + usuario + "\"}";
        MvcResult r = mvc.perform(post("/api/v1/public/" + slug + "/auth/login")
                        .header(HttpHeaders.USER_AGENT, navegador)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn();

        return "Bearer " + mapper.readTree(r.getResponse().getContentAsString()).get("token").asText();
    }

    private String superadmin() throws Exception {

        MvcResult r = mvc.perform(post("/api/v1/superadmin/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"" + SUPERADMIN_USUARIO + "\",\"contrasena\":\"" + SUPERADMIN_CONTRASENA + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        return "Bearer " + mapper.readTree(r.getResponse().getContentAsString()).get("token").asText();
    }

    private JsonNode sesiones(String sa, String slug) throws Exception {

        MvcResult r = mvc.perform(get("/api/v1/superadmin/instituciones/" + slug + "/sesiones").header("Authorization", sa))
                .andExpect(status().isOk())
                .andReturn();

        return mapper.readTree(r.getResponse().getContentAsString());
    }

    private String codigoDe(JsonNode lista, String navegador) {

        for (JsonNode sesion : lista.get("pagina").get("contenido")) {
            if (sesion.get("navegador").asText().equals(navegador)) {
                return sesion.get("codigo").asText();
            }
        }
        throw new AssertionError("No aparece la sesion de " + navegador);
    }

    @Test
    void elSuperadminVeLasSesionesYLasCierraUnaPorUnaYTodas() throws Exception {

        String sa = superadmin();
        String enCelular = entrar("sesiones-a", "7100", CELULAR);
        String enComputador = entrar("sesiones-a", "7100", COMPUTADOR);
        String estudiante = entrar("sesiones-a", "7200", CELULAR);

        JsonNode lista = sesiones(sa, "sesiones-a");
        assertThat(lista.get("resumen").get("sesiones").asInt()).isEqualTo(3);
        assertThat(lista.get("resumen").get("usuarios").asInt()).isEqualTo(2);
        assertThat(lista.get("resumen").get("enLinea").asInt()).isEqualTo(3);
        assertThat(lista.get("resumen").get("docentes").asInt()).isEqualTo(2);
        assertThat(lista.get("resumen").get("estudiantes").asInt()).isEqualTo(1);

        JsonNode primera = lista.get("pagina").get("contenido").get(0);
        assertThat(primera.get("documento").asText()).isIn("7100", "7200");
        assertThat(primera.has("usuarioId")).isFalse();

        // filtro por rol
        mvc.perform(get("/api/v1/superadmin/instituciones/sesiones-a/sesiones?rol=ESTUDIANTE").header("Authorization", sa))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagina.totalElementos").value(1))
                .andExpect(jsonPath("$.pagina.contenido[0].dispositivo").value("Celular"))
                .andExpect(jsonPath("$.pagina.contenido[0].sistema").value("Android"));

        // una sola: la del computador (firefox). la del celular sigue
        String codigo = codigoDe(lista, "Firefox");
        mvc.perform(delete("/api/v1/superadmin/instituciones/sesiones-a/sesiones/" + codigo).header("Authorization", sa))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/auth/yo").header("Authorization", enComputador)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/yo").header("Authorization", enCelular)).andExpect(status().isOk());
        assertThat(sesiones(sa, "sesiones-a").get("resumen").get("sesiones").asInt()).isEqualTo(2);

        // otra vez la misma: ya no existe
        mvc.perform(delete("/api/v1/superadmin/instituciones/sesiones-a/sesiones/" + codigo).header("Authorization", sa))
                .andExpect(status().isNotFound());

        // todas
        mvc.perform(delete("/api/v1/superadmin/instituciones/sesiones-a/sesiones").header("Authorization", sa))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cerradas").value(2));

        mvc.perform(get("/api/v1/auth/yo").header("Authorization", enCelular)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/yo").header("Authorization", estudiante)).andExpect(status().isUnauthorized());
        assertThat(sesiones(sa, "sesiones-a").get("resumen").get("sesiones").asInt()).isZero();

        // puede volver a entrar
        String otraVez = entrar("sesiones-a", "7100", CELULAR);
        mvc.perform(get("/api/v1/auth/yo").header("Authorization", otraVez)).andExpect(status().isOk());
    }

    @Test
    void unaSesionDeOtroColegioNoSeCierraPorEsteEnlace() throws Exception {

        String sa = superadmin();
        String docenteB = entrar("sesiones-b", "7300", COMPUTADOR);
        String codigo = codigoDe(sesiones(sa, "sesiones-b"), "Firefox");

        mvc.perform(delete("/api/v1/superadmin/instituciones/sesiones-a/sesiones/" + codigo).header("Authorization", sa))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/v1/auth/yo").header("Authorization", docenteB)).andExpect(status().isOk());
    }

    @Test
    void alSalirLaSesionSeCierraYDejaDeVerse() throws Exception {

        String sa = superadmin();
        String docente = entrar("sesiones-c", "7400", CELULAR);
        assertThat(sesiones(sa, "sesiones-c").get("resumen").get("sesiones").asInt()).isEqualTo(1);

        mvc.perform(post("/api/v1/auth/salir").header("Authorization", docente)).andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/auth/yo").header("Authorization", docente)).andExpect(status().isUnauthorized());
        assertThat(sesiones(sa, "sesiones-c").get("resumen").get("sesiones").asInt()).isZero();
    }
}
