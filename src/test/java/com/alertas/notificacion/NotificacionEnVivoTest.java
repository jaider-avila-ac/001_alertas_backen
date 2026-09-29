package com.alertas.notificacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alertas.auth.model.Rol;
import com.alertas.soporte.IntegracionTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

// con servidor de verdad en un puerto, para abrir un websocket real
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class NotificacionEnVivoTest extends IntegracionTest {

    @LocalServerPort
    int puerto;

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    private JsonNode leer(MvcResult resultado) throws Exception {
        return mapper.readTree(resultado.getResponse().getContentAsString());
    }

    private String crearPersonal(Colegio colegio, String documento, Rol rol) throws Exception {

        String body = "{\"tipoDoc\":\"CC\",\"nroDoc\":\"" + documento + "\",\"nombres\":\"Persona\",\"apellidos\":\"Prueba\","
                + "\"rol\":\"" + rol.name() + "\"}";
        mvc.perform(post("/api/v1/personal").header("Authorization", colegio.admin())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        Long usuario = idUsuario(colegio.id(), documento);
        OWNER.update("UPDATE usuarios SET usu_debe_cambiar_contrasena = false WHERE usu_id = ?", usuario);
        return token(usuario, colegio.id(), colegio.slug(), rol);
    }

    private String ticket(String token) throws Exception {
        return leer(mvc.perform(post("/api/v1/notificaciones/ticket").header("Authorization", token))
                .andExpect(status().isOk()).andReturn()).get("ticket").asText();
    }

    // abre el websocket y deja lo que llegue en la cola
    private WebSocketSession conectar(String ticket, BlockingQueue<String> recibidos) throws Exception {

        TextWebSocketHandler manejador = new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession sesion, TextMessage mensaje) {
                recibidos.add(mensaje.getPayload());
            }
        };

        return new StandardWebSocketClient()
                .execute(manejador, "ws://localhost:" + puerto + "/ws/notificaciones?ticket=" + ticket)
                .get(5, TimeUnit.SECONDS);
    }

    @Test
    void laNotificacionLlegaAlInstantePorWebsocket() throws Exception {

        Colegio colegio = crearColegioCompleto(mvc, mapper, "vivo-avisos", "91910000");
        String psicorientador = crearPersonal(colegio, "9191003", Rol.PSICORIENTADOR);
        String docente = crearPersonal(colegio, "9191002", Rol.DOCENTE);

        // grupo, estudiante y categoria
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
        Map<String, Object> estudiante = new HashMap<>();
        estudiante.put("tipoDoc", "TI");
        estudiante.put("nroDoc", "9191001");
        estudiante.put("nombres", "Ana");
        estudiante.put("apellidos", "Rios");
        estudiante.put("grupoId", grupo);
        String codigo = leer(mvc.perform(post("/api/v1/estudiantes").header("Authorization", colegio.admin())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(estudiante)))
                .andReturn()).get("codigo").asText();
        Long categoria = leer(mvc.perform(get("/api/v1/categorias").header("Authorization", docente)).andReturn())
                .get(0).get("id").asLong();

        // el psicorientador abre la app
        BlockingQueue<String> recibidos = new LinkedBlockingQueue<>();
        String miTicket = ticket(psicorientador);
        WebSocketSession sesion = conectar(miTicket, recibidos);
        assertThat(sesion.isOpen()).isTrue();

        // el ticket no sirve dos veces, ni uno inventado
        assertThatThrownBy(() -> conectar(miTicket, new LinkedBlockingQueue<>())).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> conectar("inventado", new LinkedBlockingQueue<>())).isInstanceOf(Exception.class);

        // el docente crea una alerta critica: al psicorientador le llega en el momento
        String alerta = "{\"estudianteCodigo\":\"" + codigo + "\",\"categoriaId\":" + categoria
                + ",\"nivel\":\"CRITICO\",\"descripcion\":\"Situacion de prueba en el salon\"}";
        mvc.perform(post("/api/v1/alertas").header("Authorization", docente)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(alerta))
                .andExpect(status().isCreated());

        String aviso = recibidos.poll(5, TimeUnit.SECONDS);
        assertThat(aviso).isNotNull();
        JsonNode mensaje = mapper.readTree(aviso);
        assertThat(mensaje.get("tipo").asText()).isEqualTo("nueva");
        assertThat(mensaje.get("notificacion").get("titulo").asText()).isEqualTo("Alerta prioritaria");

        // al marcar leidas, sus otras pestanas se enteran
        mvc.perform(patch("/api/v1/notificaciones/leidas").header("Authorization", psicorientador))
                .andExpect(status().isOk());
        String leidas = recibidos.poll(5, TimeUnit.SECONDS);
        assertThat(mapper.readTree(leidas).get("tipo").asText()).isEqualTo("leidas");

        // al docente no le llega nada de esto
        assertThat(recibidos.poll(500, TimeUnit.MILLISECONDS)).isNull();

        sesion.close();
    }
}
