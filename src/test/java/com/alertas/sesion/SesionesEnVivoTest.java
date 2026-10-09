package com.alertas.sesion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alertas.auth.model.Rol;
import com.alertas.soporte.IntegracionTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

// el panel del superadmin recibe cada cambio al instante y el usuario cerrado recibe el aviso de salida
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SesionesEnVivoTest extends IntegracionTest {

    static final String NAVEGADOR = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
            + "Chrome/129.0.0.0 Safari/537.36";

    @LocalServerPort
    int puerto;

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @BeforeAll
    static void datos() {
        Long colegio = crearInstitucion("sesiones-vivo");
        crearUsuario(colegio, "8100", Rol.DOCENTE, new BCryptPasswordEncoder().encode("8100"), false);
    }

    private JsonNode leer(MvcResult r) throws Exception {
        return mapper.readTree(r.getResponse().getContentAsString());
    }

    private WebSocketSession conectar(String ruta, BlockingQueue<String> recibidos) throws Exception {

        TextWebSocketHandler manejador = new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession sesion, TextMessage mensaje) {
                recibidos.add(mensaje.getPayload());
            }
        };

        return new StandardWebSocketClient().execute(manejador, "ws://localhost:" + puerto + ruta).get(5, TimeUnit.SECONDS);
    }

    // el siguiente aviso de ese tipo (se saltan los de actividad que puedan llegar en medio)
    private JsonNode esperar(BlockingQueue<String> recibidos, String tipo) throws Exception {

        for (int i = 0; i < 10; i++) {
            String texto = recibidos.poll(5, TimeUnit.SECONDS);
            if (texto == null) {
                break;
            }
            JsonNode json = mapper.readTree(texto);
            if (json.get("tipo").asText().equals(tipo)) {
                return json;
            }
        }
        throw new AssertionError("No llego el aviso " + tipo);
    }

    @Test
    void elPanelSeEnteraAlInstanteYElUsuarioCerradoSale() throws Exception {

        String sa = "Bearer " + leer(mvc.perform(post("/api/v1/superadmin/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"" + SUPERADMIN_USUARIO + "\",\"contrasena\":\"" + SUPERADMIN_CONTRASENA + "\"}"))
                .andReturn()).get("token").asText();

        String ticketPanel = leer(mvc.perform(post("/api/v1/superadmin/sesiones/ticket").header("Authorization", sa))
                .andExpect(status().isOk()).andReturn()).get("ticket").asText();

        BlockingQueue<String> panel = new LinkedBlockingQueue<>();
        WebSocketSession conexionPanel = conectar("/ws/superadmin?ticket=" + ticketPanel, panel);

        // entra la docente: al panel le llega la fila nueva
        String docente = "Bearer " + leer(mvc.perform(post("/api/v1/public/sesiones-vivo/auth/login")
                        .header(HttpHeaders.USER_AGENT, NAVEGADOR)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"usuario\":\"8100\",\"contrasena\":\"8100\"}"))
                .andExpect(status().isOk()).andReturn()).get("token").asText();

        JsonNode abierta = esperar(panel, "abierta");
        assertThat(abierta.get("slug").asText()).isEqualTo("sesiones-vivo");
        assertThat(abierta.get("sesion").get("documento").asText()).isEqualTo("8100");
        assertThat(abierta.get("sesion").get("navegador").asText()).isEqualTo("Chrome");
        assertThat(abierta.get("resumen").get("sesiones").asInt()).isEqualTo(1);
        assertThat(abierta.has("institucionId")).isFalse();
        String codigo = abierta.get("codigo").asText();

        // la docente tiene abierta la campana
        String ticketDocente = leer(mvc.perform(post("/api/v1/notificaciones/ticket").header("Authorization", docente))
                .andExpect(status().isOk()).andReturn()).get("ticket").asText();
        BlockingQueue<String> campana = new LinkedBlockingQueue<>();
        WebSocketSession conexionDocente = conectar("/ws/notificaciones?ticket=" + ticketDocente, campana);

        // el superadmin la cierra: ella recibe el aviso y el panel quita la fila
        mvc.perform(delete("/api/v1/superadmin/instituciones/sesiones-vivo/sesiones/" + codigo).header("Authorization", sa))
                .andExpect(status().isNoContent());

        assertThat(esperar(campana, "sesion-cerrada")).isNotNull();

        JsonNode cerrada = esperar(panel, "cerrada");
        assertThat(cerrada.get("codigo").asText()).isEqualTo(codigo);
        assertThat(cerrada.get("rol").asText()).isEqualTo("DOCENTE");
        assertThat(cerrada.get("resumen").get("sesiones").asInt()).isZero();

        conexionPanel.close();
        conexionDocente.close();
    }

    @Test
    void sinTicketValidoNoHayConexion() {

        BlockingQueue<String> recibidos = new LinkedBlockingQueue<>();
        boolean conecto;
        try {
            conectar("/ws/superadmin?ticket=inventado", recibidos);
            conecto = true;
        } catch (Exception e) {
            conecto = false;
        }
        assertThat(conecto).isFalse();
    }
}
