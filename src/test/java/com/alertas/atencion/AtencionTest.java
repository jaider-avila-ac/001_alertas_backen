package com.alertas.atencion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alertas.auth.model.Rol;
import com.alertas.soporte.IntegracionTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

class AtencionTest extends IntegracionTest {

    static final int ESTE_ANIO = LocalDate.now().getYear();

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    // ---------------------------------------------------------------- ayudas

    private JsonNode leer(MvcResult resultado) throws Exception {
        return mapper.readTree(resultado.getResponse().getContentAsString());
    }

    private JsonNode obtener(String ruta, String token) throws Exception {
        return leer(mvc.perform(get(ruta).header("Authorization", token)).andExpect(status().isOk()).andReturn());
    }

    record Escenario(Colegio colegio, Long grupoId, String docente, String psi1, String psi2, Long categoriaId) {
    }

    private Escenario escenario(String slug, String documentoAdmin) throws Exception {

        Colegio colegio = crearColegioCompleto(mvc, mapper, slug, documentoAdmin);
        Long grupo = crearGrupo(colegio, "Octavo", "A");
        String docente = crearPersonal(colegio, documentoAdmin + "2", "Docente", Rol.DOCENTE);
        String psi1 = crearPersonal(colegio, documentoAdmin + "3", "Laura", Rol.PSICORIENTADOR);
        String psi2 = crearPersonal(colegio, documentoAdmin + "4", "Marta", Rol.PSICORIENTADOR);
        Long categoria = obtener("/api/v1/categorias", colegio.admin()).get(0).get("id").asLong();
        return new Escenario(colegio, grupo, docente, psi1, psi2, categoria);
    }

    private Long crearGrupo(Colegio colegio, String grado, String nombre) throws Exception {

        Long anio = null;
        for (JsonNode fila : obtener("/api/v1/anios-lectivos", colegio.admin())) {
            if (fila.get("anio").asInt() == ESTE_ANIO) {
                anio = fila.get("id").asLong();
            }
        }
        Long gradoId = null;
        for (JsonNode fila : obtener("/api/v1/grados", colegio.admin())) {
            if (fila.get("nombre").asText().equals(grado)) {
                gradoId = fila.get("id").asLong();
            }
        }
        String body = "{\"anioId\":" + anio + ",\"gradoId\":" + gradoId + ",\"nombre\":\"" + nombre + "\"}";
        return leer(mvc.perform(post("/api/v1/grupos").header("Authorization", colegio.admin())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
    }

    private String crearEstudiante(Escenario e, String documento, String nombres) throws Exception {

        Map<String, Object> datos = new HashMap<>();
        datos.put("tipoDoc", "TI");
        datos.put("nroDoc", documento);
        datos.put("nombres", nombres);
        datos.put("apellidos", "Prueba");
        datos.put("grupoId", e.grupoId());
        return leer(mvc.perform(post("/api/v1/estudiantes").header("Authorization", e.colegio().admin())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(datos)))
                .andExpect(status().isCreated()).andReturn()).get("codigo").asText();
    }

    private String crearPersonal(Colegio colegio, String documento, String nombres, Rol rol) throws Exception {

        String body = "{\"tipoDoc\":\"CC\",\"nroDoc\":\"" + documento + "\",\"nombres\":\"" + nombres
                + "\",\"apellidos\":\"Prueba\",\"rol\":\"" + rol.name() + "\"}";
        mvc.perform(post("/api/v1/personal").header("Authorization", colegio.admin())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        Long usuario = idUsuario(colegio.id(), documento);
        OWNER.update("UPDATE usuarios SET usu_debe_cambiar_contrasena = false WHERE usu_id = ?", usuario);
        return token(usuario, colegio.id(), colegio.slug(), rol);
    }

    private String codigoPersonal(Colegio colegio, String documento) {
        return OWNER.queryForObject("SELECT per_codigo FROM personal WHERE per_ins_id = ? AND per_nro_doc = ?",
                String.class, colegio.id(), documento);
    }

    private String crearAlerta(Escenario e, String estudiante, String nivel) throws Exception {

        Map<String, Object> datos = new HashMap<>();
        datos.put("estudianteCodigo", estudiante);
        datos.put("categoriaId", e.categoriaId());
        datos.put("nivel", nivel);
        datos.put("descripcion", "Situacion observada en clase de prueba");
        return leer(mvc.perform(post("/api/v1/alertas").header("Authorization", e.docente())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(datos)))
                .andExpect(status().isCreated()).andReturn()).get("codigo").asText();
    }

    private ResultActions agendar(String token, String estudiante, OffsetDateTime inicio, List<String> excluir) throws Exception {

        Map<String, Object> datos = new HashMap<>();
        datos.put("estudianteCodigo", estudiante);
        datos.put("inicio", inicio.toString());
        datos.put("duracionMinutos", 45);
        datos.put("modalidad", "PRESENCIAL");
        datos.put("lugar", "Orientacion");
        datos.put("indicacion", "Trae tu cuaderno");
        datos.put("excluir", excluir);
        return mvc.perform(post("/api/v1/citas").header("Authorization", token)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(datos)));
    }

    private String estadoAlerta(String codigo) {
        return OWNER.queryForObject("SELECT ale_estado FROM alertas WHERE ale_codigo = ?", String.class, codigo);
    }

    private JsonNode pestana(String token, String nombre) throws Exception {
        return obtener("/api/v1/atencion/mis-estudiantes?pestana=" + nombre, token);
    }

    // ---------------------------------------------------------------- pruebas

    @Test
    void deLaBandejaALaCitaYElResultado() throws Exception {

        Escenario e = escenario("aten-flujo", "41410000");
        String ana = crearEstudiante(e, "4141001", "Ana");
        String leve = crearAlerta(e, ana, "LEVE");
        String critica = crearAlerta(e, ana, "CRITICO");

        // bandeja: un estudiante con dos alertas, la mayor critica
        mvc.perform(get("/api/v1/atencion/bandeja").header("Authorization", e.psi1()))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].alertas").value(2))
                .andExpect(jsonPath("$.contenido[0].nivelMaximo").value("CRITICO"))
                .andExpect(jsonPath("$.contenido[0].prioritaria").value(true));

        // la toma Laura; Marta llega tarde
        mvc.perform(post("/api/v1/atencion/estudiantes/" + ana + "/tomar").header("Authorization", e.psi1()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.esMio").value(true))
                .andExpect(jsonPath("$.psicorientador").value("Laura Prueba"));
        mvc.perform(post("/api/v1/atencion/estudiantes/" + ana + "/tomar").header("Authorization", e.psi2()))
                .andExpect(status().isConflict());
        assertThat(obtener("/api/v1/atencion/bandeja", e.psi1()).get("totalElementos").asInt()).isZero();
        assertThat(pestana(e.psi1(), "POR_AGENDAR").get("totalElementos").asInt()).isEqualTo(1);

        // cita sin la leve
        JsonNode cita = leer(agendar(e.psi1(), ana, OffsetDateTime.now().plusDays(1), List.of(leve))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PROGRAMADA"))
                .andExpect(jsonPath("$.alertas.length()").value(1))
                .andExpect(jsonPath("$.enCurso").value(false))
                .andReturn());
        String codigoCita = cita.get("codigo").asText();
        assertThat(estadoAlerta(critica)).isEqualTo("EN_PROCESO");
        assertThat(estadoAlerta(leve)).isEqualTo("PENDIENTE");
        assertThat(pestana(e.psi1(), "POR_AGENDAR").get("totalElementos").asInt()).isZero();
        assertThat(pestana(e.psi1(), "CON_CITA").get("contenido").get(0).get("citaCodigo").asText()).isEqualTo(codigoCita);

        // otra cita para el mismo estudiante no
        agendar(e.psi1(), ana, OffsetDateTime.now().plusDays(2), new ArrayList<>()).andExpect(status().isConflict());

        // una alerta nueva entra sola a la cita programada
        String nueva = crearAlerta(e, ana, "MODERADO");
        assertThat(estadoAlerta(nueva)).isEqualTo("EN_PROCESO");
        mvc.perform(get("/api/v1/citas/" + codigoCita).header("Authorization", e.psi1()))
                .andExpect(jsonPath("$.alertas.length()").value(2));

        // la hora no importa: se inicia aunque sea manana
        mvc.perform(post("/api/v1/citas/" + codigoCita + "/iniciar").header("Authorization", e.psi1()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enCurso").value(true));
        assertThat(pestana(e.psi1(), "CON_CITA").get("totalElementos").asInt()).isZero();
        assertThat(pestana(e.psi1(), "EN_CURSO").get("totalElementos").asInt()).isEqualTo(1);

        // faltando una alerta no
        String incompleto = "{\"resultados\":["
                + "{\"alertaCodigo\":\"" + critica + "\",\"resultado\":\"EN_PROCESO\",\"observacion\":\"Hablamos\"}]}";
        mvc.perform(post("/api/v1/citas/" + codigoCita + "/finalizar").header("Authorization", e.psi1())
                        .contentType(MediaType.APPLICATION_JSON).content(incompleto))
                .andExpect(status().isBadRequest());

        // cada alerta necesita su observacion
        String sinObservacion = "{\"resultados\":["
                + "{\"alertaCodigo\":\"" + critica + "\",\"resultado\":\"COMPLETADA\",\"observacion\":\"\"},"
                + "{\"alertaCodigo\":\"" + nueva + "\",\"resultado\":\"EN_PROCESO\",\"observacion\":\"Sigue\"}]}";
        mvc.perform(post("/api/v1/citas/" + codigoCita + "/finalizar").header("Authorization", e.psi1())
                        .contentType(MediaType.APPLICATION_JSON).content(sinObservacion))
                .andExpect(status().isBadRequest());

        String resultadoBody = "{\"resultados\":["
                + "{\"alertaCodigo\":\"" + critica + "\",\"resultado\":\"COMPLETADA\",\"observacion\":\"Se remitio a la EPS\"},"
                + "{\"alertaCodigo\":\"" + nueva + "\",\"resultado\":\"EN_PROCESO\",\"observacion\":\"Hay que seguir hablando\"}]}";
        mvc.perform(post("/api/v1/citas/" + codigoCita + "/finalizar").header("Authorization", e.psi1())
                        .contentType(MediaType.APPLICATION_JSON).content(resultadoBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("REALIZADA"))
                .andExpect(jsonPath("$.alertas[0].observacion").value("Se remitio a la EPS"));

        String conclusion = OWNER.queryForObject("SELECT ale_conclusion FROM alertas WHERE ale_codigo = ?", String.class, critica);
        assertThat(conclusion).isEqualTo("Se remitio a la EPS");

        assertThat(estadoAlerta(critica)).isEqualTo("COMPLETADA");
        assertThat(estadoAlerta(nueva)).isEqualTo("EN_PROCESO");
        // la leve y la nueva siguen activas: vuelve a "por agendar"
        assertThat(pestana(e.psi1(), "POR_AGENDAR").get("contenido").get(0).get("activas").asInt()).isEqualTo(2);

        // reabrir la completada
        mvc.perform(post("/api/v1/atencion/alertas/" + critica + "/reabrir").header("Authorization", e.psi1()))
                .andExpect(status().isOk());
        assertThat(estadoAlerta(critica)).isEqualTo("EN_PROCESO");

        // nivel: solo quien la atiende
        mvc.perform(patch("/api/v1/atencion/alertas/" + leve + "/nivel").header("Authorization", e.psi2())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nivel\":\"ALTO\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/atencion/alertas/" + leve + "/nivel").header("Authorization", e.psi1())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nivel\":\"ALTO\"}"))
                .andExpect(status().isOk());

        // lo que se escribe en la cita solo lo ve el psicorientador
        JsonNode delAdmin = obtener("/api/v1/atencion/estudiantes/" + ana, e.colegio().admin());
        assertThat(delAdmin.get("citas").get(0).get("alertas").get(0).get("observacion").isNull()).isTrue();
        JsonNode delPsi = obtener("/api/v1/atencion/estudiantes/" + ana, e.psi1());
        assertThat(delPsi.get("citas").get(0).get("alertas").get(0).get("observacion").asText()).isEqualTo("Se remitio a la EPS");
        assertThat(delPsi.get("alertas").size()).isEqualTo(3);

        // cada alerta guarda su historial de observaciones
        for (JsonNode alerta : delPsi.get("alertas")) {
            if (alerta.get("codigo").asText().equals(nueva)) {
                assertThat(alerta.get("seguimientos").get(0).get("observacion").asText()).isEqualTo("Hay que seguir hablando");
            }
        }
        for (JsonNode alerta : delAdmin.get("alertas")) {
            assertThat(alerta.get("seguimientos").size()).isZero();
        }

        // el docente no entra a la atencion
        mvc.perform(get("/api/v1/atencion/bandeja").header("Authorization", e.docente())).andExpect(status().isForbidden());
    }

    @Test
    void crucesReasignarCancelarYNoAsistio() throws Exception {

        Escenario e = escenario("aten-cruces", "42420000");
        String ana = crearEstudiante(e, "4242001", "Ana");
        String luis = crearEstudiante(e, "4242002", "Luis");
        String alertaAna = crearAlerta(e, ana, "ALTO");
        crearAlerta(e, luis, "LEVE");

        OffsetDateTime manana = OffsetDateTime.now().plusDays(1).withNano(0);

        // agendar tambien toma al estudiante
        String citaAna = leer(agendar(e.psi1(), ana, manana, new ArrayList<>())
                .andExpect(status().isCreated()).andReturn()).get("codigo").asText();

        // Laura no puede tener dos citas cruzadas
        agendar(e.psi1(), luis, manana.plusMinutes(30), new ArrayList<>())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Ya tienes una cita que se cruza con ese horario"));

        // Marta no puede agendarle a Ana (la atiende Laura)
        agendar(e.psi2(), ana, manana.plusDays(1), new ArrayList<>()).andExpect(status().isConflict());

        // el admin reasigna a Ana a Marta: la cita se va con ella
        String body = "{\"psicorientadorCodigo\":\"" + codigoPersonal(e.colegio(), "424200004")
                + "\",\"motivo\":\"Laura sale a vacaciones\"}";
        mvc.perform(post("/api/v1/atencion/estudiantes/" + ana + "/reasignar").header("Authorization", e.colegio().admin())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.psicorientador").value("Marta Prueba"));

        mvc.perform(post("/api/v1/citas/" + citaAna + "/cancelar").header("Authorization", e.psi1())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"x\"}"))
                .andExpect(status().isForbidden());

        // Marta la cancela: la alerta no cambia y Ana vuelve a "por agendar"
        mvc.perform(post("/api/v1/citas/" + citaAna + "/cancelar").header("Authorization", e.psi2())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Paro de transporte\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADA"))
                .andExpect(jsonPath("$.motivoCancelacion").value("Paro de transporte"));
        assertThat(estadoAlerta(alertaAna)).isEqualTo("EN_PROCESO");
        assertThat(pestana(e.psi2(), "POR_AGENDAR").get("totalElementos").asInt()).isEqualTo(1);

        // otra cita, se reprograma y el estudiante no asiste
        String otra = leer(agendar(e.psi2(), ana, manana.plusHours(3), new ArrayList<>())
                .andExpect(status().isCreated()).andReturn()).get("codigo").asText();

        String reprogramar = "{\"inicio\":\"" + manana.plusHours(5) + "\",\"duracionMinutos\":30,\"modalidad\":\"VIRTUAL\","
                + "\"lugar\":\"https://meet.example/abc\"}";
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/citas/" + otra)
                        .header("Authorization", e.psi2())
                        .contentType(MediaType.APPLICATION_JSON).content(reprogramar))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modalidad").value("VIRTUAL"));

        // en la agenda de la semana de Marta
        String desde = LocalDate.now().toString();
        String hasta = LocalDate.now().plusDays(7).toString();
        JsonNode agenda = obtener("/api/v1/citas/agenda?desde=" + desde + "&hasta=" + hasta, e.psi2());
        assertThat(agenda.size()).isEqualTo(2);

        // no asistio tampoco depende de la hora
        mvc.perform(post("/api/v1/citas/" + otra + "/no-asistio").header("Authorization", e.psi2()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("NO_ASISTIO"));
        assertThat(estadoAlerta(alertaAna)).isEqualTo("EN_PROCESO");

        // ya cerrada no se cambia
        mvc.perform(post("/api/v1/citas/" + otra + "/no-asistio").header("Authorization", e.psi2()))
                .andExpect(status().isConflict());
    }

    @Test
    void alInactivarAlPsicorientadorSusCasosVuelvenALaBandeja() throws Exception {

        Escenario e = escenario("aten-liberar", "43430000");
        String ana = crearEstudiante(e, "4343001", "Ana");
        String luis = crearEstudiante(e, "4343002", "Luis");
        String alertaAna = crearAlerta(e, ana, "ALTO");
        crearAlerta(e, luis, "LEVE");

        // Laura atiende a Ana y ya le agendo cita (la alerta queda en proceso)
        String cita = leer(agendar(e.psi1(), ana, OffsetDateTime.now().plusDays(1), new ArrayList<>())
                .andExpect(status().isCreated()).andReturn()).get("codigo").asText();
        String laura = codigoPersonal(e.colegio(), "434300003");

        // el admin ve cuantos casos abiertos tiene antes de inactivarla
        mvc.perform(get("/api/v1/personal/" + laura).header("Authorization", e.colegio().admin()))
                .andExpect(jsonPath("$.casosAbiertos").value(1));

        mvc.perform(patch("/api/v1/personal/" + laura + "/estado").header("Authorization", e.colegio().admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.casosAbiertos").value(0));

        // la cita se cancelo y la alerta sigue en proceso pero sin psicorientador
        String estadoCita = OWNER.queryForObject("SELECT cit_estado FROM citas WHERE cit_codigo = ?", String.class, cita);
        assertThat(estadoCita).isEqualTo("CANCELADA");
        assertThat(estadoAlerta(alertaAna)).isEqualTo("EN_PROCESO");
        Long psi = OWNER.queryForObject("SELECT ale_psi_id FROM alertas WHERE ale_codigo = ?", Long.class, alertaAna);
        assertThat(psi).isNull();

        // Marta la ve en la bandeja, marcada, y la puede tomar
        JsonNode bandeja = obtener("/api/v1/atencion/bandeja", e.psi2());
        assertThat(bandeja.get("totalElementos").asInt()).isEqualTo(2);
        for (JsonNode fila : bandeja.get("contenido")) {
            if (fila.get("codigo").asText().equals(ana)) {
                assertThat(fila.get("veniaEnAtencion").asBoolean()).isTrue();
            }
        }
        mvc.perform(post("/api/v1/atencion/estudiantes/" + ana + "/tomar").header("Authorization", e.psi2()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.psicorientador").value("Marta Prueba"));

        // en bloque tambien: se inactivan todos los psicorientadores y Ana vuelve a quedar sin asignar
        mvc.perform(patch("/api/v1/personal/estado-masivo").header("Authorization", e.colegio().admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rol\":\"PSICORIENTADOR\",\"activo\":false}"))
                .andExpect(status().isOk());
        Long psiDespues = OWNER.queryForObject("SELECT ale_psi_id FROM alertas WHERE ale_codigo = ?", Long.class, alertaAna);
        assertThat(psiDespues).isNull();
    }
}
