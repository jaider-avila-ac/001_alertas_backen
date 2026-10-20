package com.alertas.categoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alertas.auth.model.Rol;
import com.alertas.soporte.IntegracionTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

// las categorias son fijas: las mismas para todos los colegios y siempre activas
class CategoriaTest extends IntegracionTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    private JsonNode leer(MvcResult resultado) throws Exception {
        return mapper.readTree(resultado.getResponse().getContentAsString());
    }

    private JsonNode listar(String token) throws Exception {
        return leer(mvc.perform(get("/api/v1/categorias").header("Authorization", token))
                .andExpect(status().isOk()).andReturn());
    }

    private String docente(Colegio colegio, String documento) {
        Long usuario = crearUsuario(colegio.id(), documento, Rol.DOCENTE, "x", false);
        return token(usuario, colegio.id(), colegio.slug(), Rol.DOCENTE);
    }

    @Test
    void todosLosColegiosUsanElMismoCatalogo() throws Exception {

        Colegio colegio = crearColegioCompleto(mvc, mapper, "cat-inicio", "21210000");
        Colegio otro = crearColegioCompleto(mvc, mapper, "cat-inicio-otro", "21210001");

        JsonNode categorias = listar(docente(colegio, "2101"));
        JsonNode delOtro = listar(otro.admin());

        assertThat(categorias.size()).isEqualTo(10);
        assertThat(categorias.get(0).get("nombre").asText()).isEqualTo("Asistencia escolar");
        for (int i = 0; i < categorias.size(); i++) {
            // la misma fila: mismo id en los dos colegios
            assertThat(categorias.get(i).get("id").asLong()).isEqualTo(delOtro.get(i).get("id").asLong());
            assertThat(categorias.get(i).get("nombre").asText()).isEqualTo(delOtro.get(i).get("nombre").asText());
        }
    }

    @Test
    void nadieLasCreaCambiaNiApaga() throws Exception {

        Colegio colegio = crearColegioCompleto(mvc, mapper, "cat-fijas", "22220000");
        long id = listar(colegio.admin()).get(0).get("id").asLong();

        mvc.perform(post("/api/v1/categorias").header("Authorization", colegio.admin())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Embarazo adolescente\"}"))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(put("/api/v1/categorias/" + id).header("Authorization", colegio.admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Otra cosa\"}"))
                .andExpect(status().is4xxClientError());
        mvc.perform(patch("/api/v1/categorias/" + id + "/estado").header("Authorization", colegio.admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}"))
                .andExpect(status().is4xxClientError());
        mvc.perform(delete("/api/v1/categorias/" + id).header("Authorization", colegio.admin()))
                .andExpect(status().is4xxClientError());

        assertThat(listar(colegio.admin()).size()).isEqualTo(10);
    }

    @Test
    void cadaColegioCuentaSoloSusAlertas() throws Exception {

        Colegio colegio = crearColegioCompleto(mvc, mapper, "cat-alertas", "24240000");
        long categoria = listar(colegio.admin()).get(0).get("id").asLong();

        // una alerta de prueba metida directo
        Long usuario = crearUsuario(colegio.id(), "2401", Rol.ESTUDIANTE, "x", false);
        Long estudiante = OWNER.queryForObject("""
                INSERT INTO estudiantes (est_ins_id, est_usu_id, est_codigo, est_codigo_qr, est_tipo_doc, est_nro_doc,
                                         est_nombres, est_apellidos)
                VALUES (?, ?, 'catEst000001', 'catQr0000001', 'TI', '2401', 'Ana', 'Rios') RETURNING est_id""",
                Long.class, colegio.id(), usuario);
        Long reporta = idUsuario(colegio.id(), "24240000");
        OWNER.update("""
                INSERT INTO alertas (ale_ins_id, ale_codigo, ale_est_id, ale_origen, ale_reportada_por, ale_cat_id,
                                     ale_nivel, ale_descripcion)
                VALUES (?, 'catAlerta001', ?, 'DOCENTE', ?, ?, 'LEVE', 'Descripcion de prueba')""",
                colegio.id(), estudiante, reporta, categoria);

        assertThat(listar(colegio.admin()).get(0).get("totalAlertas").asLong()).isEqualTo(1);

        // la misma categoria en otro colegio no cuenta esa alerta
        Colegio otro = crearColegioCompleto(mvc, mapper, "cat-alertas-otro", "24240001");
        assertThat(listar(otro.admin()).get(0).get("totalAlertas").asLong()).isZero();
    }
}
