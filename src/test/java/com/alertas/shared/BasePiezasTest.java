package com.alertas.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alertas.auth.model.Rol;
import com.alertas.auth.service.LimiteIntentosService;
import com.alertas.shared.idempotencia.IdempotenciaInterceptor;
import com.alertas.soporte.IntegracionTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class BasePiezasTest extends IntegracionTest {

    static Long colegio;

    @Autowired
    MockMvc mvc;

    @Autowired
    LimiteIntentosService limiteIntentos;

    @BeforeAll
    static void datos() {
        colegio = crearInstitucion("piezas");
    }

    private String docente() {
        return token(5L, colegio, "piezas", Rol.DOCENTE);
    }

    @Test
    void dobleClicSoloPasaUnaVez() throws Exception {
        String llave = UUID.randomUUID().toString();
        mvc.perform(post("/api/v1/prueba/idempotente").header("Authorization", docente())
                        .header(IdempotenciaInterceptor.CABECERA, llave))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/prueba/idempotente").header("Authorization", docente())
                        .header(IdempotenciaInterceptor.CABECERA, llave))
                .andExpect(status().isConflict());
        // otra llave es otro envio
        mvc.perform(post("/api/v1/prueba/idempotente").header("Authorization", docente())
                        .header(IdempotenciaInterceptor.CABECERA, UUID.randomUUID().toString()))
                .andExpect(status().isOk());
    }

    @Test
    void sinLlaveDeIdempotenciaEs400() throws Exception {
        mvc.perform(post("/api/v1/prueba/idempotente").header("Authorization", docente()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Falta la cabecera Idempotency-Key"));
    }

    @Test
    void siFallaSePuedeReintentarConLaMismaLlave() throws Exception {
        String llave = UUID.randomUUID().toString();
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/v1/prueba/idempotente-falla").header("Authorization", docente())
                            .header(IdempotenciaInterceptor.CABECERA, llave))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("fallo a proposito"));
        }
    }

    @Test
    void errorDeValidacionDevuelveElMensajeDelCampo() throws Exception {
        mvc.perform(post("/api/v1/prueba/validacion").header("Authorization", docente())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El nombre es obligatorio"));
    }

    @Test
    void jsonMalFormadoEs400() throws Exception {
        mvc.perform(post("/api/v1/prueba/validacion").header("Authorization", docente())
                        .contentType(MediaType.APPLICATION_JSON).content("{nombre"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La solicitud no tiene el formato esperado"));
    }

    @Test
    void loginSeBloqueaDespuesDeCincoFallos() {

        String usuario = "piezas:" + UUID.randomUUID();
        String ip = "ip:" + UUID.randomUUID();

        for (int i = 0; i < 4; i++) {
            limiteIntentos.registrarFallo(usuario, ip);
        }
        assertThat(limiteIntentos.estaBloqueado(usuario, ip)).isFalse();

        limiteIntentos.registrarFallo(usuario, ip);
        assertThat(limiteIntentos.estaBloqueado(usuario, ip)).isTrue();

        // a otro usuario desde la misma ip no le afecta: la ip aguanta mas (red del colegio)
        assertThat(limiteIntentos.estaBloqueado("piezas:otro", ip)).isFalse();
    }

    @Test
    void laIpSeBloqueaConMuchosFallosDeUsuariosDistintos() {

        String ip = "ip:" + UUID.randomUUID();

        for (int i = 0; i < 50; i++) {
            limiteIntentos.registrarFallo("piezas:" + UUID.randomUUID(), ip);
        }

        assertThat(limiteIntentos.estaBloqueado("piezas:cualquiera", ip)).isTrue();
    }

    @Test
    void entrarBienLimpiaLosFallosDelUsuario() {

        String usuario = "piezas:" + UUID.randomUUID();

        for (int i = 0; i < 5; i++) {
            limiteIntentos.registrarFallo(usuario, "ip:" + UUID.randomUUID());
        }
        assertThat(limiteIntentos.estaBloqueado(usuario, "ip:nueva")).isTrue();

        limiteIntentos.limpiar(usuario);
        assertThat(limiteIntentos.estaBloqueado(usuario, "ip:nueva")).isFalse();
    }

    @Test
    void healthEstaArribaConBdYRedis() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
