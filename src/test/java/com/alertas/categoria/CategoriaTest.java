package com.alertas.categoria;

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
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

class CategoriaTest extends IntegracionTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    private JsonNode leer(MvcResult resultado) throws Exception {
        return mapper.readTree(resultado.getResponse().getContentAsString());
    }

    private JsonNode listar(String token, String estado) throws Exception {
        return leer(mvc.perform(get("/api/v1/categorias").param("estado", estado).header("Authorization", token))
                .andExpect(status().isOk()).andReturn());
    }

    private ResultActions crear(Colegio colegio, String nombre) throws Exception {
        return mvc.perform(post("/api/v1/categorias").header("Authorization", colegio.admin())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"" + nombre + "\"}"));
    }

    private ResultActions cambiarEstado(Colegio colegio, long id, boolean activa) throws Exception {
        return mvc.perform(patch("/api/v1/categorias/" + id + "/estado").header("Authorization", colegio.admin())
                .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":" + activa + "}"));
    }

    private String docente(Colegio colegio, String documento) {
        Long usuario = crearUsuario(colegio.id(), documento, Rol.DOCENTE, "x", false);
        return token(usuario, colegio.id(), colegio.slug(), Rol.DOCENTE);
    }

    @Test
    void elColegioArrancaConLasPredeterminadasYElDocenteLasVe() throws Exception {

        Colegio colegio = crearColegioCompleto(mvc, mapper, "cat-inicio", "21210000");

        JsonNode categorias = listar(docente(colegio, "2101"), "activas");
        assertThat(categorias.size()).isEqualTo(6);
        assertThat(categorias.get(0).get("nombre").asText()).isEqualTo("Ausentismo escolar");

        // el docente no las cambia
        mvc.perform(post("/api/v1/categorias").header("Authorization", docente(colegio, "2102"))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Nueva\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void crearRenombrarYNoRepetirNombres() throws Exception {

        Colegio colegio = crearColegioCompleto(mvc, mapper, "cat-crud", "22220000");

        long id = leer(crear(colegio, "  Embarazo   adolescente ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Embarazo adolescente"))
                .andReturn()).get("id").asLong();

        // ni con otras mayusculas ni con tildes
        crear(colegio, "SALUD MÉNTAL")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Ya existe la categoria SALUD MÉNTAL"));
        crear(colegio, "ab").andExpect(status().isBadRequest());

        mvc.perform(put("/api/v1/categorias/" + id).header("Authorization", colegio.admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Embarazo en adolescentes\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Embarazo en adolescentes"));

        // renombrar con su mismo nombre no choca consigo misma
        mvc.perform(put("/api/v1/categorias/" + id).header("Authorization", colegio.admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"embarazo en adolescentes\"}"))
                .andExpect(status().isOk());

        // la de otro colegio no existe aqui
        Colegio otro = crearColegioCompleto(mvc, mapper, "cat-crud-otro", "22220001");
        mvc.perform(put("/api/v1/categorias/" + id).header("Authorization", otro.admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Robada\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void desactivarYNoDejarElColegioSinCategorias() throws Exception {

        Colegio colegio = crearColegioCompleto(mvc, mapper, "cat-estado", "23230000");
        JsonNode todas = listar(colegio.admin(), "todas");

        long primera = todas.get(0).get("id").asLong();
        cambiarEstado(colegio, primera, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activa").value(false));

        assertThat(listar(colegio.admin(), "activas").size()).isEqualTo(5);
        assertThat(listar(colegio.admin(), "todas").size()).isEqualTo(6);

        // se apagan todas menos una
        for (int i = 1; i < todas.size() - 1; i++) {
            cambiarEstado(colegio, todas.get(i).get("id").asLong(), false).andExpect(status().isOk());
        }

        long ultima = todas.get(todas.size() - 1).get("id").asLong();
        cambiarEstado(colegio, ultima, false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Debe quedar al menos una categoria activa para poder crear alertas"));
        mvc.perform(delete("/api/v1/categorias/" + ultima).header("Authorization", colegio.admin()))
                .andExpect(status().isConflict());

        // una apagada sin alertas si se puede borrar
        mvc.perform(delete("/api/v1/categorias/" + primera).header("Authorization", colegio.admin()))
                .andExpect(status().isNoContent());
        assertThat(listar(colegio.admin(), "todas").size()).isEqualTo(5);
    }

    @Test
    void conAlertasNoSeBorra() throws Exception {

        Colegio colegio = crearColegioCompleto(mvc, mapper, "cat-alertas", "24240000");
        long categoria = listar(colegio.admin(), "todas").get(0).get("id").asLong();

        // una alerta de prueba metida directo: todavia no existe el modulo de alertas
        Long anio = OWNER.queryForObject("SELECT anl_id FROM anios_lectivos WHERE anl_ins_id = ? AND anl_activo",
                Long.class, colegio.id());
        Long grado = OWNER.queryForObject("SELECT gra_id FROM grados WHERE gra_ins_id = ? AND gra_orden = 6",
                Long.class, colegio.id());
        Long grupo = OWNER.queryForObject("""
                INSERT INTO grupos (grp_ins_id, grp_anl_id, grp_gra_id, grp_nombre) VALUES (?, ?, ?, 'A') RETURNING grp_id""",
                Long.class, colegio.id(), anio, grado);
        Long usuario = crearUsuario(colegio.id(), "2401", Rol.ESTUDIANTE, "x", false);
        Long estudiante = OWNER.queryForObject("""
                INSERT INTO estudiantes (est_ins_id, est_usu_id, est_codigo, est_codigo_qr, est_tipo_doc, est_nro_doc,
                                         est_nombres, est_apellidos)
                VALUES (?, ?, 'catEst000001', 'catQr0000001', 'TI', '2401', 'Ana', 'Rios') RETURNING est_id""",
                Long.class, colegio.id(), usuario);
        Long matricula = OWNER.queryForObject("""
                INSERT INTO matriculas (mat_ins_id, mat_est_id, mat_anl_id, mat_grp_id) VALUES (?, ?, ?, ?) RETURNING mat_id""",
                Long.class, colegio.id(), estudiante, anio, grupo);
        Long reporta = idUsuario(colegio.id(), "24240000");
        OWNER.update("""
                INSERT INTO alertas (ale_ins_id, ale_codigo, ale_est_id, ale_origen, ale_reportada_por, ale_cat_id,
                                     ale_nivel, ale_descripcion, ale_anl_id, ale_grp_id, ale_mat_id)
                VALUES (?, 'catAlerta001', ?, 'DOCENTE', ?, ?, 'LEVE', 'Descripcion de prueba', ?, ?, ?)""",
                colegio.id(), estudiante, reporta, categoria, anio, grupo, matricula);

        assertThat(listar(colegio.admin(), "todas").get(0).get("totalAlertas").asLong()).isEqualTo(1);

        mvc.perform(delete("/api/v1/categorias/" + categoria).header("Authorization", colegio.admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("La categoria tiene 1 alertas, no se puede borrar. Desactivala"));
    }
}
