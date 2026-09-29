package com.alertas.valoracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alertas.auth.model.Rol;
import com.alertas.soporte.IntegracionTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class ValoracionTest extends IntegracionTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    private JsonNode leer(MvcResult resultado) throws Exception {
        return mapper.readTree(resultado.getResponse().getContentAsString());
    }

    private String crearPersonal(Colegio colegio, String documento, Rol rol) throws Exception {

        String body = "{\"tipoDoc\":\"CC\",\"nroDoc\":\"" + documento + "\",\"nombres\":\"Orienta" + documento + "\","
                + "\"apellidos\":\"Prueba\",\"rol\":\"" + rol.name() + "\"}";
        mvc.perform(post("/api/v1/personal").header("Authorization", colegio.admin())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        Long usuario = idUsuario(colegio.id(), documento);
        OWNER.update("UPDATE usuarios SET usu_debe_cambiar_contrasena = false WHERE usu_id = ?", usuario);
        return token(usuario, colegio.id(), colegio.slug(), rol);
    }

    private Long crearGrupo(Colegio colegio) throws Exception {

        Long anio = null;
        for (JsonNode fila : leer(mvc.perform(get("/api/v1/anios-lectivos").header("Authorization", colegio.admin())).andReturn())) {
            if (fila.get("anio").asInt() == LocalDate.now().getYear()) {
                anio = fila.get("id").asLong();
            }
        }
        Long grado = null;
        for (JsonNode fila : leer(mvc.perform(get("/api/v1/grados").header("Authorization", colegio.admin())).andReturn())) {
            if (fila.get("nombre").asText().equals("Sexto")) {
                grado = fila.get("id").asLong();
            }
        }
        return leer(mvc.perform(post("/api/v1/grupos").header("Authorization", colegio.admin())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"anioId\":" + anio + ",\"gradoId\":" + grado + ",\"nombre\":\"A\"}"))
                .andReturn()).get("id").asLong();
    }

    private String crearEstudiante(Colegio colegio, Long grupo, String documento, String nombre) throws Exception {

        String body = "{\"tipoDoc\":\"TI\",\"nroDoc\":\"" + documento + "\",\"nombres\":\"" + nombre + "\","
                + "\"apellidos\":\"Prueba\",\"grupoId\":" + grupo + "}";
        return leer(mvc.perform(post("/api/v1/estudiantes").header("Authorization", colegio.admin())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn()).get("codigo").asText();
    }

    private void configurar(Colegio colegio, boolean activas, int dias) throws Exception {
        mvc.perform(patch("/api/v1/institucion/valoraciones").header("Authorization", colegio.admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activas\":" + activas + ",\"dias\":" + dias + "}"))
                .andExpect(status().isOk());
    }

    private JsonNode estudiantes(String psicorientador, String filtro) throws Exception {
        return leer(mvc.perform(get("/api/v1/valoraciones/estudiantes").param("filtro", filtro)
                        .header("Authorization", psicorientador))
                .andExpect(status().isOk()).andReturn()).get("contenido");
    }

    private MvcResult registrar(String psicorientador, String estudiante, String observacion) throws Exception {
        return mvc.perform(post("/api/v1/valoraciones").header("Authorization", psicorientador)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estudianteCodigo\":\"" + estudiante + "\",\"observacion\":\"" + observacion + "\"}"))
                .andReturn();
    }

    @Test
    void apagadasPorDefectoYSoloElAdminLasEnciende() throws Exception {

        Colegio colegio = crearColegioCompleto(mvc, mapper, "val-config", "61610000");
        String psicorientador = crearPersonal(colegio, "6161003", Rol.PSICORIENTADOR);
        String docente = crearPersonal(colegio, "6161002", Rol.DOCENTE);
        String estudiante = crearEstudiante(colegio, crearGrupo(colegio), "6161001", "Ana");

        // asi nace el colegio: apagadas, y el perfil se lo dice al front
        assertThat(leer(mvc.perform(get("/api/v1/auth/yo").header("Authorization", psicorientador)).andReturn())
                .get("valoraciones").asBoolean()).isFalse();
        mvc.perform(get("/api/v1/valoraciones/estudiantes").header("Authorization", psicorientador))
                .andExpect(status().isConflict());
        assertThat(registrar(psicorientador, estudiante, "Todo bien en clase").getResponse().getStatus()).isEqualTo(409);

        // ni el docente ni el psicorientador cambian la configuracion
        mvc.perform(patch("/api/v1/institucion/valoraciones").header("Authorization", psicorientador)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activas\":true,\"dias\":30}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/institucion/valoraciones").header("Authorization", docente)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activas\":true,\"dias\":30}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/institucion/valoraciones").header("Authorization", colegio.admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activas\":true,\"dias\":0}"))
                .andExpect(status().isBadRequest());

        configurar(colegio, true, 30);
        assertThat(leer(mvc.perform(get("/api/v1/auth/yo").header("Authorization", psicorientador)).andReturn())
                .get("valoraciones").asBoolean()).isTrue();
        JsonNode config = leer(mvc.perform(get("/api/v1/valoraciones/configuracion").header("Authorization", psicorientador))
                .andReturn());
        assertThat(config.get("activas").asBoolean()).isTrue();
        assertThat(config.get("dias").asInt()).isEqualTo(30);

        // el docente no registra valoraciones
        assertThat(registrar(docente, estudiante, "Todo bien en clase").getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void nuncaValoradosYPorValorarSeCalculanSolos() throws Exception {

        Colegio colegio = crearColegioCompleto(mvc, mapper, "val-lista", "62620000");
        String psicorientador = crearPersonal(colegio, "6262003", Rol.PSICORIENTADOR);
        String otro = crearPersonal(colegio, "6262004", Rol.PSICORIENTADOR);
        Long grupo = crearGrupo(colegio);
        String ana = crearEstudiante(colegio, grupo, "6262001", "Ana");
        String beto = crearEstudiante(colegio, grupo, "6262002", "Beto");
        configurar(colegio, true, 30);

        // nadie valorado: los dos por valorar y los dos nunca
        assertThat(estudiantes(psicorientador, "POR_VALORAR")).hasSize(2);
        assertThat(estudiantes(psicorientador, "NUNCA")).hasSize(2);

        MvcResult registrada = registrar(psicorientador, ana, "Se ve tranquila, buen trato con sus companeros");
        assertThat(registrada.getResponse().getStatus()).isEqualTo(201);
        assertThat(leer(registrada).get("observacion").asText()).startsWith("Se ve tranquila");

        // ana ya no le toca; beto si
        JsonNode porValorar = estudiantes(psicorientador, "POR_VALORAR");
        assertThat(porValorar).hasSize(1);
        assertThat(porValorar.get(0).get("codigo").asText()).isEqualTo(beto);
        assertThat(estudiantes(psicorientador, "NUNCA")).hasSize(1);

        // en TODOS: beto primero (nunca), ana con su ultima valoracion y quien la hizo
        JsonNode todos = estudiantes(psicorientador, "TODOS");
        assertThat(todos).hasSize(2);
        assertThat(todos.get(0).get("codigo").asText()).isEqualTo(beto);
        assertThat(todos.get(0).get("ultima").isNull()).isTrue();
        assertThat(todos.get(1).get("ultimaPor").asText()).isEqualTo("Orienta6262003 Prueba");
        assertThat(todos.get(1).get("porValorar").asBoolean()).isFalse();

        // pasan mas dias que los que dijo el admin: ana vuelve a quedar por valorar, pero ya no es "nunca"
        OWNER.update("UPDATE valoraciones SET val_creado_en = now() - interval '40 days' WHERE val_ins_id = ?", colegio.id());
        assertThat(estudiantes(psicorientador, "POR_VALORAR")).hasSize(2);
        assertThat(estudiantes(psicorientador, "NUNCA")).hasSize(1);

        // si el admin sube el plazo, ana deja de estar por valorar
        configurar(colegio, true, 60);
        assertThat(estudiantes(psicorientador, "POR_VALORAR")).hasSize(1);

        // la de otro psicorientador tambien cuenta
        registrar(otro, beto, "Revision de rutina sin novedades");
        JsonNode despues = estudiantes(psicorientador, "TODOS");
        for (JsonNode fila : despues) {
            if (fila.get("codigo").asText().equals(beto)) {
                assertThat(fila.get("ultimaPor").asText()).isEqualTo("Orienta6262004 Prueba");
            }
        }
        assertThat(estudiantes(psicorientador, "POR_VALORAR")).isEmpty();

        // si el admin las apaga, la lista no se puede ver
        configurar(colegio, false, 60);
        mvc.perform(get("/api/v1/valoraciones/estudiantes").header("Authorization", psicorientador))
                .andExpect(status().isConflict());
    }

    @Test
    void laValoracionNoEsAlertaYLaAlertaDelPsicorientadorNaceEnProceso() throws Exception {

        Colegio colegio = crearColegioCompleto(mvc, mapper, "val-alerta", "63630000");
        String psicorientador = crearPersonal(colegio, "6363003", Rol.PSICORIENTADOR);
        String estudiante = crearEstudiante(colegio, crearGrupo(colegio), "6363001", "Carla");
        configurar(colegio, true, 30);

        registrar(psicorientador, estudiante, "Se nota decaida, dice que no duerme bien");

        // no hay ninguna alerta: el expediente la muestra aparte
        Integer alertas = OWNER.queryForObject("SELECT count(*) FROM alertas WHERE ale_ins_id = ?", Integer.class, colegio.id());
        assertThat(alertas).isZero();
        JsonNode expediente = leer(mvc.perform(get("/api/v1/atencion/estudiantes/" + estudiante)
                .header("Authorization", psicorientador)).andReturn());
        assertThat(expediente.get("alertas")).isEmpty();
        assertThat(expediente.get("valoraciones")).hasSize(1);
        assertThat(expediente.get("valoraciones").get(0).get("observacion").asText()).startsWith("Se nota decaida");
        assertThat(expediente.get("valoraciones").get(0).get("gradoNombre").asText()).isEqualTo("Sexto");

        // el admin ve que hubo valoracion, pero no lo que se escribio
        JsonNode delAdmin = leer(mvc.perform(get("/api/v1/atencion/estudiantes/" + estudiante)
                .header("Authorization", colegio.admin())).andReturn());
        assertThat(delAdmin.get("valoraciones")).hasSize(1);
        assertThat(delAdmin.get("valoraciones").get(0).get("observacion").isNull()).isTrue();

        // vio algo: crea la alerta. nace en proceso, es suya y no pasa por la bandeja
        Long categoria = leer(mvc.perform(get("/api/v1/categorias").header("Authorization", colegio.admin())).andReturn())
                .get(0).get("id").asLong();
        JsonNode alerta = leer(mvc.perform(post("/api/v1/alertas").header("Authorization", psicorientador)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estudianteCodigo\":\"" + estudiante + "\",\"categoriaId\":" + categoria
                                + ",\"nivel\":\"LEVE\",\"descripcion\":\"Problemas de sueno y animo bajo\"}"))
                .andExpect(status().isCreated()).andReturn());
        assertThat(alerta.get("estado").asText()).isEqualTo("EN_PROCESO");
        assertThat(alerta.get("psicorientador").asText()).isEqualTo("Orienta6363003 Prueba");
        assertThat(alerta.get("reportadaPor").asText()).isEqualTo("Orienta6363003 Prueba");

        JsonNode bandeja = leer(mvc.perform(get("/api/v1/atencion/bandeja").header("Authorization", psicorientador))
                .andReturn()).get("contenido");
        assertThat(bandeja).isEmpty();

        // queda en sus casos, lista para agendar la cita
        JsonNode porAgendar = leer(mvc.perform(get("/api/v1/atencion/mis-estudiantes").param("pestana", "POR_AGENDAR")
                .header("Authorization", psicorientador)).andReturn()).get("contenido");
        assertThat(porAgendar).hasSize(1);
        assertThat(porAgendar.get(0).get("codigo").asText()).isEqualTo(estudiante);
    }
}
