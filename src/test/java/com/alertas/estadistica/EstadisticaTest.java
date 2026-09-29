package com.alertas.estadistica;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alertas.auth.model.Rol;
import com.alertas.soporte.IntegracionTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class EstadisticaTest extends IntegracionTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    private JsonNode leer(MvcResult resultado) throws Exception {
        return mapper.readTree(resultado.getResponse().getContentAsString());
    }

    private String crearPersonal(Colegio colegio, String documento, Rol rol) throws Exception {

        String body = "{\"tipoDoc\":\"CC\",\"nroDoc\":\"" + documento + "\",\"nombres\":\"Persona\","
                + "\"apellidos\":\"Prueba\",\"rol\":\"" + rol.name() + "\"}";
        mvc.perform(post("/api/v1/personal").header("Authorization", colegio.admin())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        Long usuario = idUsuario(colegio.id(), documento);
        OWNER.update("UPDATE usuarios SET usu_debe_cambiar_contrasena = false WHERE usu_id = ?", usuario);
        return token(usuario, colegio.id(), colegio.slug(), rol);
    }

    private Long anioActivo(Colegio colegio) throws Exception {
        for (JsonNode fila : leer(mvc.perform(get("/api/v1/anios-lectivos").header("Authorization", colegio.admin())).andReturn())) {
            if (fila.get("anio").asInt() == LocalDate.now().getYear()) {
                return fila.get("id").asLong();
            }
        }
        throw new IllegalStateException("sin anio activo");
    }

    private Long crearGrupo(Colegio colegio, Long anio, String nombre) throws Exception {

        Long grado = null;
        for (JsonNode fila : leer(mvc.perform(get("/api/v1/grados").header("Authorization", colegio.admin())).andReturn())) {
            if (fila.get("nombre").asText().equals("Sexto")) {
                grado = fila.get("id").asLong();
            }
        }
        return leer(mvc.perform(post("/api/v1/grupos").header("Authorization", colegio.admin())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"anioId\":" + anio + ",\"gradoId\":" + grado + ",\"nombre\":\"" + nombre + "\"}"))
                .andReturn()).get("id").asLong();
    }

    private String crearEstudiante(Colegio colegio, Long grupo, String documento, String genero) throws Exception {

        String body = "{\"tipoDoc\":\"TI\",\"nroDoc\":\"" + documento + "\",\"nombres\":\"Est" + documento + "\","
                + "\"apellidos\":\"Prueba\",\"genero\":\"" + genero + "\",\"grupoId\":" + grupo + "}";
        return leer(mvc.perform(post("/api/v1/estudiantes").header("Authorization", colegio.admin())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn()).get("codigo").asText();
    }

    private void crearAlerta(String docente, String estudiante, Long categoria, String nivel) throws Exception {

        String body = "{\"estudianteCodigo\":\"" + estudiante + "\",\"categoriaId\":" + categoria
                + ",\"nivel\":\"" + nivel + "\",\"descripcion\":\"Situacion observada en clase\"}";
        mvc.perform(post("/api/v1/alertas").header("Authorization", docente)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    private JsonNode estadisticas(String token, String parametros) throws Exception {
        return leer(mvc.perform(get("/api/v1/estadisticas?" + parametros).header("Authorization", token))
                .andExpect(status().isOk()).andReturn());
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
    void cuentaAlertasCitasYValoracionesConSusFiltros() throws Exception {

        Colegio colegio = crearColegioCompleto(mvc, mapper, "est-resumen", "71710000");
        String docente = crearPersonal(colegio, "7171002", Rol.DOCENTE);
        String psicorientador = crearPersonal(colegio, "7171003", Rol.PSICORIENTADOR);
        Long anio = anioActivo(colegio);
        Long grupoA = crearGrupo(colegio, anio, "A");
        Long grupoB = crearGrupo(colegio, anio, "B");
        String ana = crearEstudiante(colegio, grupoA, "7171011", "F");
        String beto = crearEstudiante(colegio, grupoB, "7171012", "M");

        JsonNode categorias = leer(mvc.perform(get("/api/v1/categorias").header("Authorization", colegio.admin())).andReturn());
        Long cat1 = categorias.get(0).get("id").asLong();
        Long cat2 = categorias.get(1).get("id").asLong();

        crearAlerta(docente, ana, cat1, "ALTO");
        crearAlerta(docente, ana, cat2, "LEVE");
        crearAlerta(docente, beto, cat1, "MODERADO");

        // beto pide ayuda el mismo
        Long usuarioBeto = idUsuario(colegio.id(), "7171012");
        OWNER.update("UPDATE usuarios SET usu_debe_cambiar_contrasena = false WHERE usu_id = ?", usuarioBeto);
        mvc.perform(post("/api/v1/alertas/solicitud-ayuda")
                        .header("Authorization", token(usuarioBeto, colegio.id(), colegio.slug(), Rol.ESTUDIANTE))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoriaId\":" + cat2 + ",\"urgencia\":\"MEDIA\",\"descripcion\":\"Necesito hablar con alguien\"}"))
                .andExpect(status().isCreated());

        // la psicorientadora toma a ana, le agenda cita para manana y ana no asiste
        mvc.perform(post("/api/v1/atencion/estudiantes/" + ana + "/tomar").header("Authorization", psicorientador))
                .andExpect(status().isOk());
        String cita = leer(mvc.perform(post("/api/v1/citas").header("Authorization", psicorientador)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estudianteCodigo\":\"" + ana + "\",\"inicio\":\"" + OffsetDateTime.now().plusDays(1)
                                + "\",\"duracionMinutos\":45,\"modalidad\":\"PRESENCIAL\"}"))
                .andExpect(status().isCreated()).andReturn()).get("codigo").asText();
        mvc.perform(post("/api/v1/citas/" + cita + "/no-asistio").header("Authorization", psicorientador))
                .andExpect(status().isOk());

        // y una valoracion de rutina a beto
        mvc.perform(patch("/api/v1/institucion/valoraciones")
                        .header("Authorization", colegio.admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activas\":true,\"dias\":30}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/valoraciones").header("Authorization", psicorientador)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estudianteCodigo\":\"" + beto + "\",\"observacion\":\"Sin novedad\"}"))
                .andExpect(status().isCreated());

        JsonNode todo = estadisticas(colegio.admin(), "anioId=" + anio);
        JsonNode ind = todo.get("indicadores");
        assertThat(ind.get("alertas").asLong()).isEqualTo(4);
        assertThat(ind.get("pendientes").asLong()).isEqualTo(2);
        assertThat(ind.get("enProceso").asLong()).isEqualTo(2);
        assertThat(ind.get("completadas").asLong()).isZero();
        assertThat(ind.get("estudiantes").asLong()).isEqualTo(2);
        assertThat(ind.get("citasNoAsistio").asLong()).isEqualTo(1);
        assertThat(ind.get("citasRealizadas").asLong()).isZero();
        assertThat(ind.get("alertasConCita").asLong()).isEqualTo(2);
        // la cita es para manana: cerca de 24 horas
        assertThat(ind.get("horasPrimeraCita").asDouble()).isBetween(20.0, 26.0);
        assertThat(ind.get("valoraciones").asLong()).isEqualTo(1);

        assertThat(todo.get("porMes")).hasSize(1);
        assertThat(todo.get("porMes").get(0).get("total").asLong()).isEqualTo(4);
        assertThat(total(todo.get("porNivel"), "ALTO")).isEqualTo(1);
        assertThat(total(todo.get("porNivel"), "MODERADO")).isEqualTo(2);
        assertThat(total(todo.get("porNivel"), "CRITICO")).isZero();
        assertThat(total(todo.get("porGenero"), "F")).isEqualTo(2);
        assertThat(total(todo.get("porGenero"), "M")).isEqualTo(2);
        assertThat(total(todo.get("porOrigen"), "DOCENTE")).isEqualTo(3);
        assertThat(total(todo.get("porOrigen"), "ESTUDIANTE")).isEqualTo(1);
        assertThat(total(todo.get("porGrupo"), "Sexto A")).isEqualTo(2);
        assertThat(total(todo.get("porGrupo"), "Sexto B")).isEqualTo(2);
        assertThat(todo.get("porCategoria")).hasSize(2);
        assertThat(todo.get("porPsicorientador")).hasSize(1);
        assertThat(todo.get("porPsicorientador").get(0).get("atendidas").asLong()).isEqualTo(2);

        // filtros
        assertThat(estadisticas(colegio.admin(), "grupoId=" + grupoA).get("indicadores").get("alertas").asLong()).isEqualTo(2);
        assertThat(estadisticas(colegio.admin(), "categoriaId=" + cat1).get("indicadores").get("alertas").asLong()).isEqualTo(2);
        String manana = LocalDate.now().plusDays(1).toString();
        assertThat(estadisticas(colegio.admin(), "desde=" + manana).get("indicadores").get("alertas").asLong()).isZero();
        assertThat(estadisticas(colegio.admin(), "hasta=" + LocalDate.now()).get("indicadores").get("alertas").asLong()).isEqualTo(4);
        mvc.perform(get("/api/v1/estadisticas?desde=" + manana + "&hasta=" + LocalDate.now()).header("Authorization", colegio.admin()))
                .andExpect(status().isBadRequest());

        // el psicorientador tambien las ve; el docente no
        assertThat(estadisticas(psicorientador, "").get("indicadores").get("alertas").asLong()).isEqualTo(4);
        mvc.perform(get("/api/v1/estadisticas").header("Authorization", docente)).andExpect(status().isForbidden());

        // otro colegio no ve nada de este
        Colegio otro = crearColegioCompleto(mvc, mapper, "est-otro", "72720000");
        assertThat(estadisticas(otro.admin(), "").get("indicadores").get("alertas").asLong()).isZero();

        // excel con el resumen y una hoja por grafico
        byte[] excel = mvc.perform(get("/api/v1/estadisticas/excel?anioId=" + anio).header("Authorization", colegio.admin()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (Workbook libro = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
            assertThat(libro.getNumberOfSheets()).isEqualTo(8);
            Sheet resumen = libro.getSheet("Resumen");
            boolean encontrado = false;
            for (Row fila : resumen) {
                if (fila.getCell(0) != null && "Alertas".equals(fila.getCell(0).getStringCellValue())) {
                    assertThat(fila.getCell(1).getNumericCellValue()).isEqualTo(4.0);
                    encontrado = true;
                }
            }
            assertThat(encontrado).isTrue();
            assertThat(libro.getSheet("Por categoria").getLastRowNum()).isEqualTo(2);
        }
    }
}
