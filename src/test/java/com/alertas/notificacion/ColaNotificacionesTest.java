package com.alertas.notificacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alertas.auth.model.Rol;
import com.alertas.notificacion.service.ColaNotificacionesService;
import com.alertas.soporte.IntegracionTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.http.MediaType;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class ColaNotificacionesTest extends IntegracionTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    ColaNotificacionesService cola;

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
        Long usuario = idUsuario(colegio.id(), documento);
        OWNER.update("UPDATE usuarios SET usu_debe_cambiar_contrasena = false WHERE usu_id = ?", usuario);
        return token(usuario, colegio.id(), colegio.slug(), rol);
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

    private List<Map<String, Object>> notificaciones(Colegio colegio) {
        return OWNER.queryForList("SELECT * FROM notificaciones WHERE not_ins_id = ? ORDER BY not_id", colegio.id());
    }

    @SuppressWarnings("unchecked")
    private MapRecord<String, String, String> comoTexto(MapRecord<String, Object, Object> registro) {
        Map<String, String> campos = new HashMap<>();
        for (Map.Entry<Object, Object> campo : registro.getValue().entrySet()) {
            campos.put(String.valueOf(campo.getKey()), String.valueOf(campo.getValue()));
        }
        return StreamRecords.newRecord().in(ColaNotificacionesService.COLA).withId(registro.getId()).ofMap(campos);
    }

    @Test
    void laAccionEncolaYElConsumidorGuardaUnaSolaVez() throws Exception {

        Colegio colegio = crearColegioCompleto(mvc, mapper, "cola-guarda", "91910000");
        String docente = crearPersonal(colegio, "9191002", Rol.DOCENTE);
        crearPersonal(colegio, "9191003", Rol.PSICORIENTADOR);
        String estudiante = crearEstudiante(colegio, "9191001");
        Long categoria = leer(mvc.perform(get("/api/v1/categorias").header("Authorization", colegio.admin())).andReturn())
                .get(0).get("id").asLong();

        mvc.perform(post("/api/v1/alertas").header("Authorization", docente)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estudianteCodigo\":\"" + estudiante + "\",\"categoriaId\":" + categoria
                                + ",\"nivel\":\"ALTO\",\"descripcion\":\"Situacion observada en clase\"}"))
                .andExpect(status().isCreated());

        esperarNotificaciones();
        List<Map<String, Object>> guardadas = notificaciones(colegio);
        assertThat(guardadas).hasSize(1);
        String colaId = (String) guardadas.get(0).get("not_cola_id");
        assertThat(colaId).isNotNull();

        // el mismo mensaje otra vez (como si el servidor se cayera antes de confirmar): no se duplica
        List<MapRecord<String, Object, Object>> registro =
                redisPruebas.opsForStream().range(ColaNotificacionesService.COLA, Range.just(colaId));
        assertThat(registro).hasSize(1);
        cola.procesar(comoTexto(registro.get(0)));
        assertThat(notificaciones(colegio)).hasSize(1);
    }

    @Test
    void loQueFallaSeReintentaYDespuesQuedaEnFallidas() throws Exception {

        Colegio colegio = crearColegioCompleto(mvc, mapper, "cola-falla", "92920000");

        // un usuario que no existe: la base no deja guardarla
        Map<String, String> campos = new HashMap<>();
        campos.put("institucionId", String.valueOf(colegio.id()));
        campos.put("usuarioId", "999999999");
        campos.put("tipo", "PRUEBA");
        campos.put("titulo", "No se puede guardar");
        campos.put("mensaje", "Usuario inexistente");
        RecordId id = redisPruebas.opsForStream().add(
                StreamRecords.newRecord().in(ColaNotificacionesService.COLA).ofMap(campos));

        // el consumidor lo intenta, falla y lo deja sin confirmar
        boolean pendiente = false;
        for (int i = 0; i < 200 && !pendiente; i++) {
            for (PendingMessage mensaje : redisPruebas.opsForStream().pending(
                    ColaNotificacionesService.COLA, ColaNotificacionesService.GRUPO, Range.unbounded(), 100)) {
                if (mensaje.getId().equals(id)) {
                    pendiente = true;
                }
            }
            Thread.sleep(25);
        }
        assertThat(pendiente).isTrue();
        assertThat(notificaciones(colegio)).isEmpty();

        // sin esperar los 30 segundos: se reintenta hasta el maximo y pasa a fallidas
        Object servicio = AopTestUtils.getTargetObject(cola);
        ReflectionTestUtils.setField(servicio, "esperaReintento", Duration.ZERO);
        try {
            for (int i = 0; i < 8; i++) {
                cola.reintentarPendientes();
            }
        } finally {
            ReflectionTestUtils.setField(servicio, "esperaReintento", Duration.ofSeconds(30));
        }

        boolean sigue = false;
        for (PendingMessage mensaje : redisPruebas.opsForStream().pending(
                ColaNotificacionesService.COLA, ColaNotificacionesService.GRUPO, Range.unbounded(), 100)) {
            if (mensaje.getId().equals(id)) {
                sigue = true;
            }
        }
        assertThat(sigue).isFalse();

        boolean apartada = false;
        for (MapRecord<String, Object, Object> fallida : redisPruebas.opsForStream()
                .range(ColaNotificacionesService.FALLIDAS, Range.unbounded())) {
            if (id.getValue().equals(fallida.getValue().get("idOriginal"))) {
                apartada = true;
            }
        }
        assertThat(apartada).isTrue();
    }
}
