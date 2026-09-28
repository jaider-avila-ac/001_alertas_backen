package com.alertas.estructura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alertas.auth.model.Rol;
import com.alertas.soporte.IntegracionTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

class EstructuraTest extends IntegracionTest {

    static final int ESTE_ANIO = LocalDate.now().getYear();

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    // colegio creado por la api del superadmin, asi trae grados y anio activo como en la realidad
    record Colegio(Long id, String slug, String admin) {
    }

    private Colegio crearColegio(String slug) throws Exception {

        MvcResult login = mvc.perform(post("/api/v1/superadmin/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"" + SUPERADMIN_USUARIO + "\",\"contrasena\":\"" + SUPERADMIN_CONTRASENA + "\"}"))
                .andReturn();
        String sa = "Bearer " + leer(login).get("token").asText();

        String body = mapper.writeValueAsString(Map.of(
                "institucion", Map.of("nombre", "Colegio " + slug, "slug", slug),
                "administrador", Map.of("tipoDoc", "CC", "nroDoc", "90909090", "nombres", "Admin", "apellidos", "Prueba")));

        JsonNode creada = leer(mvc.perform(post("/api/v1/superadmin/instituciones").header("Authorization", sa)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn());

        Long id = idInstitucion(slug);
        Long adminId = idUsuario(id, "90909090");
        return new Colegio(id, slug, token(adminId, id, slug, Rol.ADMIN));
    }

    private JsonNode leer(MvcResult resultado) throws Exception {
        return mapper.readTree(resultado.getResponse().getContentAsString());
    }

    private JsonNode obtener(String ruta, String token) throws Exception {
        return leer(mvc.perform(get(ruta).header("Authorization", token)).andExpect(status().isOk()).andReturn());
    }

    private Long idDelGrado(Colegio colegio, String nombre) throws Exception {

        for (JsonNode grado : obtener("/api/v1/grados", colegio.admin())) {
            if (grado.get("nombre").asText().equals(nombre)) {
                return grado.get("id").asLong();
            }
        }
        throw new IllegalStateException("no existe el grado " + nombre);
    }

    private Long idDelAnio(Colegio colegio, int anio) throws Exception {

        for (JsonNode fila : obtener("/api/v1/anios-lectivos", colegio.admin())) {
            if (fila.get("anio").asInt() == anio) {
                return fila.get("id").asLong();
            }
        }
        throw new IllegalStateException("no existe el anio " + anio);
    }

    private ResultActions crearGrupo(Colegio colegio, Long anioId, Long gradoId, String nombre) throws Exception {

        String body = "{\"anioId\":" + anioId + ",\"gradoId\":" + gradoId + ",\"nombre\":\"" + nombre + "\"}";
        return mvc.perform(post("/api/v1/grupos").header("Authorization", colegio.admin())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions crearAnio(Colegio colegio, int anio) throws Exception {

        return mvc.perform(post("/api/v1/anios-lectivos").header("Authorization", colegio.admin())
                .contentType(MediaType.APPLICATION_JSON).content("{\"anio\":" + anio + "}"));
    }

    @Test
    void gradosEnOrdenYSoloDoceActivos() throws Exception {

        Colegio colegio = crearColegio("estr-grados");
        JsonNode grados = obtener("/api/v1/grados", colegio.admin());

        assertThat(grados.size()).isEqualTo(14);
        assertThat(grados.get(0).get("nombre").asText()).isEqualTo("Prejardin");
        assertThat(grados.get(13).get("nombre").asText()).isEqualTo("Once");

        int activos = 0;
        for (JsonNode grado : grados) {
            if (grado.get("activo").asBoolean()) {
                activos++;
            }
        }
        assertThat(activos).isEqualTo(12);
    }

    @Test
    void crearYActivarAnioDejaUnoSoloActivo() throws Exception {

        Colegio colegio = crearColegio("estr-anios");

        crearAnio(colegio, ESTE_ANIO + 1)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.activo").value(false));
        crearAnio(colegio, ESTE_ANIO + 1).andExpect(status().isConflict());
        crearAnio(colegio, 1990).andExpect(status().isBadRequest());

        Long siguiente = idDelAnio(colegio, ESTE_ANIO + 1);

        mvc.perform(patch("/api/v1/anios-lectivos/" + siguiente + "/activar").header("Authorization", colegio.admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(true));

        Integer activos = OWNER.queryForObject(
                "SELECT count(*) FROM anios_lectivos WHERE anl_ins_id = ? AND anl_activo", Integer.class, colegio.id());
        Integer activo = OWNER.queryForObject(
                "SELECT anl_anio FROM anios_lectivos WHERE anl_ins_id = ? AND anl_activo", Integer.class, colegio.id());
        assertThat(activos).isEqualTo(1);
        assertThat(activo).isEqualTo(ESTE_ANIO + 1);
    }

    @Test
    void borrarAnioSoloSiNoEstaActivoYNoTieneGrupos() throws Exception {

        Colegio colegio = crearColegio("estr-borrar-anio");
        Long actual = idDelAnio(colegio, ESTE_ANIO);

        mvc.perform(delete("/api/v1/anios-lectivos/" + actual).header("Authorization", colegio.admin()))
                .andExpect(status().isConflict());

        crearAnio(colegio, ESTE_ANIO + 2).andExpect(status().isCreated());
        Long futuro = idDelAnio(colegio, ESTE_ANIO + 2);
        crearGrupo(colegio, futuro, idDelGrado(colegio, "Sexto"), "A").andExpect(status().isCreated());

        mvc.perform(delete("/api/v1/anios-lectivos/" + futuro).header("Authorization", colegio.admin()))
                .andExpect(status().isConflict());

        crearAnio(colegio, ESTE_ANIO + 3).andExpect(status().isCreated());
        mvc.perform(delete("/api/v1/anios-lectivos/" + idDelAnio(colegio, ESTE_ANIO + 3)).header("Authorization", colegio.admin()))
                .andExpect(status().isNoContent());
    }

    @Test
    void gruposSeCreanRenombranYBorran() throws Exception {

        Colegio colegio = crearColegio("estr-grupos");
        Long anio = idDelAnio(colegio, ESTE_ANIO);
        Long sexto = idDelGrado(colegio, "Sexto");

        JsonNode grupoA = leer(crearGrupo(colegio, anio, sexto, " A ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("A"))
                .andExpect(jsonPath("$.gradoNombre").value("Sexto"))
                .andReturn());
        crearGrupo(colegio, anio, sexto, "B").andExpect(status().isCreated());

        // "a" y "A" son el mismo grupo
        crearGrupo(colegio, anio, sexto, "a").andExpect(status().isConflict());

        // prejardin esta apagado
        crearGrupo(colegio, anio, idDelGrado(colegio, "Prejardin"), "A").andExpect(status().isBadRequest());

        Long grupoId = grupoA.get("id").asLong();

        mvc.perform(put("/api/v1/grupos/" + grupoId).header("Authorization", colegio.admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"B\"}"))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/v1/grupos/" + grupoId).header("Authorization", colegio.admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"C\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("C"));

        // un estudiante ubicado en el grupo C
        Long usuario = crearUsuario(colegio.id(), "70001", Rol.ESTUDIANTE, "x", false);
        Long estudiante = OWNER.queryForObject("""
                INSERT INTO estudiantes (est_ins_id, est_usu_id, est_codigo, est_codigo_qr, est_tipo_doc, est_nro_doc,
                                         est_nombres, est_apellidos)
                VALUES (?, ?, 'codigoEst001', 'codigoQr0001', 'TI', '70001', 'Ana', 'Rios') RETURNING est_id""", Long.class, colegio.id(), usuario);
        OWNER.update("INSERT INTO matriculas (mat_ins_id, mat_est_id, mat_anl_id, mat_grp_id) VALUES (?, ?, ?, ?)",
                colegio.id(), estudiante, anio, grupoId);

        JsonNode grupos = obtener("/api/v1/grupos", colegio.admin());
        assertThat(grupos.size()).isEqualTo(2);
        assertThat(grupos.get(0).get("nombre").asText()).isEqualTo("B");
        assertThat(grupos.get(1).get("totalEstudiantes").asLong()).isEqualTo(1);

        mvc.perform(delete("/api/v1/grupos/" + grupoId).header("Authorization", colegio.admin()))
                .andExpect(status().isConflict());

        Long grupoB = grupos.get(0).get("id").asLong();
        mvc.perform(delete("/api/v1/grupos/" + grupoB).header("Authorization", colegio.admin()))
                .andExpect(status().isNoContent());
    }

    @Test
    void noSeApagaUnGradoConGruposNiSeTocanAniosPasados() throws Exception {

        Colegio colegio = crearColegio("estr-reglas");
        Long actual = idDelAnio(colegio, ESTE_ANIO);
        Long septimo = idDelGrado(colegio, "Septimo");

        crearGrupo(colegio, actual, septimo, "A").andExpect(status().isCreated());

        mvc.perform(patch("/api/v1/grados/" + septimo + "/estado").header("Authorization", colegio.admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}"))
                .andExpect(status().isConflict());

        // al pasar al anio siguiente, el anio anterior ya no se edita
        crearAnio(colegio, ESTE_ANIO + 1).andExpect(status().isCreated());
        mvc.perform(patch("/api/v1/anios-lectivos/" + idDelAnio(colegio, ESTE_ANIO + 1) + "/activar")
                        .header("Authorization", colegio.admin()))
                .andExpect(status().isOk());

        crearGrupo(colegio, actual, septimo, "B")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El anio " + ESTE_ANIO + " ya paso, sus grupos no se pueden cambiar"));

        // y ahora el grado si se puede apagar: su grupo quedo en un anio pasado
        mvc.perform(patch("/api/v1/grados/" + septimo + "/estado").header("Authorization", colegio.admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));
    }

    @Test
    void unColegioNoVeNiTocaLaEstructuraDeOtro() throws Exception {

        Colegio a = crearColegio("estr-aisla-a");
        Colegio b = crearColegio("estr-aisla-b");

        JsonNode grupoDeA = leer(crearGrupo(a, idDelAnio(a, ESTE_ANIO), idDelGrado(a, "Octavo"), "A")
                .andExpect(status().isCreated()).andReturn());
        Long grupoId = grupoDeA.get("id").asLong();

        assertThat(obtener("/api/v1/grupos", b.admin()).size()).isEqualTo(0);

        mvc.perform(put("/api/v1/grupos/" + grupoId).header("Authorization", b.admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"X\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/grupos/" + grupoId).header("Authorization", b.admin()))
                .andExpect(status().isNotFound());

        // grado de A usado para crear un grupo en B
        crearGrupo(b, idDelAnio(b, ESTE_ANIO), idDelGrado(a, "Octavo"), "A").andExpect(status().isNotFound());
    }

    @Test
    void elDocenteVePeroNoCambia() throws Exception {

        Colegio colegio = crearColegio("estr-docente");
        Long docente = crearUsuario(colegio.id(), "80001", Rol.DOCENTE, "x", false);
        String tokenDocente = token(docente, colegio.id(), colegio.slug(), Rol.DOCENTE);

        mvc.perform(get("/api/v1/grados").header("Authorization", tokenDocente)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/grupos").header("Authorization", tokenDocente)).andExpect(status().isOk());

        mvc.perform(post("/api/v1/anios-lectivos").header("Authorization", tokenDocente)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"anio\":" + (ESTE_ANIO + 1) + "}"))
                .andExpect(status().isForbidden());

        String body = "{\"anioId\":" + idDelAnio(colegio, ESTE_ANIO) + ",\"gradoId\":" + idDelGrado(colegio, "Sexto")
                + ",\"nombre\":\"A\"}";
        mvc.perform(post("/api/v1/grupos").header("Authorization", tokenDocente)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }
}
