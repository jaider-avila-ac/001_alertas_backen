package com.alertas.estadistica;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alertas.auth.model.Rol;
import com.alertas.soporte.IntegracionTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.UUID;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

// las pruebas comparten la base: lo exacto se revisa filtrando por colegio
class EstadisticaGlobalTest extends IntegracionTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    EntityManagerFactory fabrica;

    private JsonNode leer(MvcResult resultado) throws Exception {
        return mapper.readTree(resultado.getResponse().getContentAsString());
    }

    private String superadmin() throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/superadmin/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"usuario\":\"" + SUPERADMIN_USUARIO + "\",\"contrasena\":\"" + SUPERADMIN_CONTRASENA + "\"}"))
                .andReturn();
        return "Bearer " + leer(r).get("token").asText();
    }

    private String crearDocente(Colegio colegio, String documento) throws Exception {
        mvc.perform(post("/api/v1/personal").header("Authorization", colegio.admin())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipoDoc\":\"CC\",\"nroDoc\":\"" + documento + "\",\"nombres\":\"Docente\","
                                + "\"apellidos\":\"Prueba\",\"rol\":\"DOCENTE\"}"))
                .andExpect(status().isCreated());
        Long usuario = idUsuario(colegio.id(), documento);
        OWNER.update("UPDATE usuarios SET usu_debe_cambiar_contrasena = false WHERE usu_id = ?", usuario);
        return token(usuario, colegio.id(), colegio.slug(), Rol.DOCENTE);
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
                        .content("{\"tipoDoc\":\"TI\",\"nroDoc\":\"" + documento + "\",\"nombres\":\"Zuleima\","
                                + "\"apellidos\":\"Confidencial\",\"grupoId\":" + grupo + "}"))
                .andExpect(status().isCreated()).andReturn()).get("codigo").asText();
    }

    private void crearAlerta(Colegio colegio, String docente, String estudiante) throws Exception {

        Long salud = null;
        for (JsonNode categoria : leer(mvc.perform(get("/api/v1/categorias").header("Authorization", colegio.admin())).andReturn())) {
            if (categoria.get("nombre").asText().equals("Salud mental")) {
                salud = categoria.get("id").asLong();
            }
        }
        mvc.perform(post("/api/v1/alertas").header("Authorization", docente)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estudianteCodigo\":\"" + estudiante + "\",\"categoriaId\":" + salud
                                + ",\"nivel\":\"ALTO\",\"descripcion\":\"Texto secreto de la alerta\"}"))
                .andExpect(status().isCreated());
    }

    private void sms(Colegio colegio, String estado, int segmentos) {
        OWNER.update("""
                INSERT INTO sms_envios (sms_ins_id, sms_evento, sms_destinatario, sms_celular, sms_mensaje, sms_segmentos, sms_estado)
                VALUES (?, 'ALERTA_CREADA', 'FAMILIAR', '3001112233', 'Hola', ?, ?)""", colegio.id(), segmentos, estado);
    }

    private long total(JsonNode conteos, String clave) {
        for (JsonNode conteo : conteos) {
            if (conteo.get("clave").asText().equals(clave)) {
                return conteo.get("total").asLong();
            }
        }
        return -1;
    }

    @Test
    void soloTotalesPorColegioMesCategoriaYSms() throws Exception {

        Colegio a = crearColegioCompleto(mvc, mapper, "global-uno", "81810000");
        Colegio b = crearColegioCompleto(mvc, mapper, "global-dos", "81810001");
        String docenteA = crearDocente(a, "8181002");
        String docenteB = crearDocente(b, "8181012");
        String estudianteA = crearEstudiante(a, "8181001");
        String estudianteB = crearEstudiante(b, "8181011");
        crearAlerta(a, docenteA, estudianteA);
        crearAlerta(a, docenteA, estudianteA);
        crearAlerta(b, docenteB, estudianteB);
        sms(a, "ENVIADO", 1);
        sms(a, "ENVIADO", 2);
        sms(a, "FALLIDO", 1);

        String sa = superadmin();

        // un colegio
        MvcResult respuesta = mvc.perform(get("/api/v1/superadmin/estadisticas").param("institucion", a.slug())
                .header("Authorization", sa)).andExpect(status().isOk()).andReturn();
        JsonNode delA = leer(respuesta);
        JsonNode resumen = delA.get("resumen");
        assertThat(resumen.get("alertas").asLong()).isEqualTo(2);
        assertThat(resumen.get("institucionesActivas").asLong()).isEqualTo(1);
        assertThat(resumen.get("smsEnviados").asLong()).isEqualTo(2);
        assertThat(resumen.get("smsFallidos").asLong()).isEqualTo(1);
        assertThat(resumen.get("smsSegmentos").asLong()).isEqualTo(3);
        assertThat(total(delA.get("usuariosPorRol"), "ADMIN")).isEqualTo(1);
        assertThat(total(delA.get("usuariosPorRol"), "DOCENTE")).isEqualTo(1);
        assertThat(total(delA.get("usuariosPorRol"), "ESTUDIANTE")).isEqualTo(1);
        assertThat(total(delA.get("usuariosPorRol"), "PSICORIENTADOR")).isZero();
        assertThat(delA.get("porMes")).hasSize(1);
        assertThat(delA.get("porMes").get(0).get("total").asLong()).isEqualTo(2);
        assertThat(total(delA.get("porCategoria"), "Salud mental")).isEqualTo(2);
        assertThat(delA.get("smsPorMes").get(0).get("segmentos").asLong()).isEqualTo(3);

        // nada de nombres ni del contenido de las alertas
        String texto = respuesta.getResponse().getContentAsString();
        assertThat(texto).doesNotContain("Zuleima").doesNotContain("Confidencial").doesNotContain("Texto secreto");

        // todos: la misma categoria de dos colegios queda en una sola fila
        JsonNode todos = leer(mvc.perform(get("/api/v1/superadmin/estadisticas").header("Authorization", sa))
                .andExpect(status().isOk()).andReturn());
        int filasSalud = 0;
        for (JsonNode categoria : todos.get("porCategoria")) {
            if (categoria.get("clave").asText().equalsIgnoreCase("salud mental")) {
                filasSalud++;
                assertThat(categoria.get("total").asLong()).isGreaterThanOrEqualTo(3);
            }
        }
        assertThat(filasSalud).isEqualTo(1);
        assertThat(todos.get("resumen").get("alertas").asLong()).isGreaterThanOrEqualTo(3);

        // la respuesta ya trae la primera pagina del comparativo y sale de una sola consulta
        assertThat(todos.get("comparativo").get("contenido").size()).isGreaterThan(0);
        assertThat(todos.get("comparativo").get("totalElementos").asLong()).isGreaterThanOrEqualTo(2);
        Statistics conteo = fabrica.unwrap(SessionFactory.class).getStatistics();
        esperarNotificaciones();
        conteo.setStatisticsEnabled(true);
        conteo.clear();
        mvc.perform(get("/api/v1/superadmin/estadisticas").param("institucion", a.slug()).header("Authorization", sa))
                .andExpect(status().isOk());
        long consultas = conteo.getPrepareStatementCount();
        conteo.setStatisticsEnabled(false);
        assertThat(consultas).isLessThanOrEqualTo(3);

        // comparativo paginado: cada colegio con sus numeros
        long alertasA = -1;
        long alertasB = -1;
        long smsA = -1;
        int pagina = 0;
        int paginas = 1;
        while (pagina < paginas) {
            JsonNode comparativo = leer(mvc.perform(get("/api/v1/superadmin/estadisticas/instituciones")
                    .param("pagina", String.valueOf(pagina)).param("tamanio", "50").header("Authorization", sa))
                    .andExpect(status().isOk()).andReturn());
            paginas = comparativo.get("totalPaginas").asInt();
            for (JsonNode fila : comparativo.get("contenido")) {
                if (fila.get("slug").asText().equals(a.slug())) {
                    alertasA = fila.get("alertas").asLong();
                    smsA = fila.get("smsSegmentos").asLong();
                    assertThat(fila.get("estudiantes").asLong()).isEqualTo(1);
                }
                if (fila.get("slug").asText().equals(b.slug())) {
                    alertasB = fila.get("alertas").asLong();
                }
            }
            pagina++;
        }
        assertThat(alertasA).isEqualTo(2);
        assertThat(alertasB).isEqualTo(1);
        assertThat(smsA).isEqualTo(3);

        // fechas: manana no hay nada
        String manana = LocalDate.now().plusDays(1).toString();
        JsonNode futuro = leer(mvc.perform(get("/api/v1/superadmin/estadisticas").param("institucion", a.slug())
                .param("desde", manana).header("Authorization", sa)).andReturn());
        assertThat(futuro.get("resumen").get("alertas").asLong()).isZero();
        mvc.perform(get("/api/v1/superadmin/estadisticas").param("desde", manana)
                        .param("hasta", LocalDate.now().toString()).header("Authorization", sa))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/superadmin/estadisticas").param("institucion", "no-existe").header("Authorization", sa))
                .andExpect(status().isNotFound());

        // el admin de un colegio no entra
        mvc.perform(get("/api/v1/superadmin/estadisticas").header("Authorization", a.admin()))
                .andExpect(status().isForbidden());
    }
}
