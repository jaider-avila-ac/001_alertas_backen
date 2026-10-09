package com.alertas.inicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alertas.auth.model.Rol;
import com.alertas.soporte.IntegracionTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class InicioTest extends IntegracionTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    EntityManagerFactory fabrica;

    private JsonNode leer(MvcResult resultado) throws Exception {
        return mapper.readTree(resultado.getResponse().getContentAsString());
    }

    private String crearPersonal(Colegio colegio, String documento, Rol rol) throws Exception {
        mvc.perform(post("/api/v1/personal").header("Authorization", colegio.admin())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipoDoc\":\"CC\",\"nroDoc\":\"" + documento + "\",\"nombres\":\"Persona\","
                                + "\"apellidos\":\"Prueba\",\"rol\":\"" + rol.name() + "\"}"))
                .andExpect(status().isCreated());
        return token(idUsuario(colegio.id(), documento), colegio.id(), colegio.slug(), rol);
    }

    private String crearEstudiante(Colegio colegio, String documento) throws Exception {

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
        Long grupo = leer(mvc.perform(post("/api/v1/grupos").header("Authorization", colegio.admin())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"anioId\":" + anio + ",\"gradoId\":" + grado + ",\"nombre\":\"A\"}"))
                .andReturn()).get("id").asLong();
        return leer(mvc.perform(post("/api/v1/estudiantes").header("Authorization", colegio.admin())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipoDoc\":\"TI\",\"nroDoc\":\"" + documento + "\",\"nombres\":\"Ana\","
                                + "\"apellidos\":\"Rios\",\"grupoId\":" + grupo + "}"))
                .andExpect(status().isCreated()).andReturn()).get("codigo").asText();
    }

    // el dashboard en una peticion; ademas cuenta las consultas a la base
    private JsonNode inicio(String token) throws Exception {

        // la cola de notificaciones escribe en otro hilo: se espera a que termine para contar solo esta peticion
        esperarNotificaciones();
        Statistics conteo = fabrica.unwrap(SessionFactory.class).getStatistics();
        conteo.setStatisticsEnabled(true);
        conteo.clear();
        JsonNode respuesta = leer(mvc.perform(get("/api/v1/inicio").header("Authorization", token))
                .andExpect(status().isOk()).andReturn());
        long consultas = conteo.getPrepareStatementCount();
        conteo.setStatisticsEnabled(false);
        assertThat(consultas).as("consultas a la base").isLessThanOrEqualTo(3);
        return respuesta;
    }

    private String tarjeta(JsonNode dashboard, String clave) {
        for (JsonNode tarjeta : dashboard.get("tarjetas")) {
            if (tarjeta.get("clave").asText().equals(clave)) {
                return tarjeta.get("valor").asText();
            }
        }
        return null;
    }

    @Test
    void cadaRolVeSuResumenEnUnaPeticionConMaximoTresConsultas() throws Exception {

        Colegio colegio = crearColegioCompleto(mvc, mapper, "inicio-roles", "73730000");
        String docente = crearPersonal(colegio, "7373002", Rol.DOCENTE);
        String psicorientador = crearPersonal(colegio, "7373003", Rol.PSICORIENTADOR);
        String ana = crearEstudiante(colegio, "7373001");
        String tokenAna = token(idUsuario(colegio.id(), "7373001"), colegio.id(), colegio.slug(), Rol.ESTUDIANTE);

        Long categoria = leer(mvc.perform(get("/api/v1/categorias").header("Authorization", colegio.admin())).andReturn())
                .get(0).get("id").asLong();
        String alerta = leer(mvc.perform(post("/api/v1/alertas").header("Authorization", docente)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estudianteCodigo\":\"" + ana + "\",\"categoriaId\":" + categoria
                                + ",\"nivel\":\"ALTO\",\"descripcion\":\"Situacion observada en clase\"}"))
                .andExpect(status().isCreated()).andReturn()).get("codigo").asText();

        // admin: conteos del colegio, estados, seis meses y la ultima alerta
        JsonNode admin = inicio(colegio.admin());
        assertThat(tarjeta(admin, "ESTUDIANTES")).isEqualTo("1");
        assertThat(tarjeta(admin, "DOCENTES")).isEqualTo("1");
        assertThat(tarjeta(admin, "PSICORIENTADORES")).isEqualTo("1");
        assertThat(tarjeta(admin, "ALERTAS")).isEqualTo("1");
        assertThat(admin.get("porMes")).hasSize(6);
        assertThat(admin.get("lista")).hasSize(1);
        assertThat(admin.get("lista").get(0).get("titulo").asText()).isEqualTo("Ana Rios");

        // psicorientador: esta en la bandeja; al tomarlo y agendar pasa a sus casos y a sus citas
        assertThat(tarjeta(inicio(psicorientador), "BANDEJA")).isEqualTo("1");
        mvc.perform(post("/api/v1/atencion/estudiantes/" + ana + "/tomar").header("Authorization", psicorientador))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/citas").header("Authorization", psicorientador)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estudianteCodigo\":\"" + ana + "\",\"inicio\":\"" + OffsetDateTime.now().plusDays(2)
                                + "\",\"duracionMinutos\":45,\"modalidad\":\"PRESENCIAL\",\"lugar\":\"Orientacion\"}"))
                .andExpect(status().isCreated());
        JsonNode psi = inicio(psicorientador);
        assertThat(tarjeta(psi, "BANDEJA")).isEqualTo("0");
        assertThat(tarjeta(psi, "CASOS")).isEqualTo("1");
        assertThat(tarjeta(psi, "VALORAR")).isNull();
        assertThat(psi.get("lista")).hasSize(1);
        assertThat(psi.get("lista").get(0).get("enlace").asText()).startsWith("/atencion/citas/");

        // docente: sus alertas, por estado y nivel, y el enlace a la suya
        JsonNode doc = inicio(docente);
        assertThat(tarjeta(doc, "ALERTAS")).isEqualTo("1");
        assertThat(tarjeta(doc, "EN_PROCESO")).isEqualTo("1");
        assertThat(doc.get("porNivel")).hasSize(4);
        assertThat(doc.get("lista").get(0).get("enlace").asText()).isEqualTo("/alertas/" + alerta);

        // estudiante: su proxima cita
        OWNER.update("UPDATE usuarios SET usu_debe_cambiar_contrasena = false WHERE usu_id = ?", idUsuario(colegio.id(), "7373001"));
        JsonNode est = inicio(tokenAna);
        assertThat(tarjeta(est, "CITA")).isNotEqualTo("Sin citas");
        assertThat(tarjeta(est, "SOLICITUDES")).isEqualTo("0");
        // quien lo acompana, su proceso (solo estados) y sus citas, sin datos de la alerta
        assertThat(tarjeta(est, "ORIENTADOR")).isNotEqualTo("Sin asignar");
        assertThat(est.get("estados")).hasSize(3);
        assertThat(est.get("estados").get(1).get("total").asLong()).isEqualTo(1);
        assertThat(est.get("lista")).hasSize(1);
        assertThat(est.get("lista").get(0).get("estado").asText()).isEqualTo("PROGRAMADA");
        assertThat(est.get("lista").get(0).get("enlace").asText()).isEqualTo("/mi-proceso");
        assertThat(est.get("lista").get(0).get("nivel").isNull()).isTrue();
    }
}
