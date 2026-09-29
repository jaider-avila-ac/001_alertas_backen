package com.alertas.sms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alertas.auth.model.Rol;
import com.alertas.soporte.IntegracionTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

// sin llaves de twilio en las pruebas: el proveedor solo registra, no sale ningun mensaje real
class SmsTest extends IntegracionTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    private JsonNode leer(MvcResult resultado) throws Exception {
        return mapper.readTree(resultado.getResponse().getContentAsString());
    }

    private String superadmin() throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/superadmin/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"usuario\":\"" + SUPERADMIN_USUARIO + "\",\"contrasena\":\"" + SUPERADMIN_CONTRASENA + "\"}"))
                .andReturn();
        return "Bearer " + leer(r).get("token").asText();
    }

    private void smsDelColegio(Colegio colegio, boolean activo) throws Exception {
        mvc.perform(patch("/api/v1/superadmin/instituciones/" + colegio.slug() + "/sms").header("Authorization", superadmin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":" + activo + "}"))
                .andExpect(status().isOk());
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

    // estudiante con celular y dos familiares: uno recibe sms y el otro no
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

        Map<String, Object> datos = new HashMap<>();
        datos.put("tipoDoc", "TI");
        datos.put("nroDoc", documento);
        datos.put("nombres", "José");
        datos.put("apellidos", "Peña");
        datos.put("celular", "3001112233");
        datos.put("grupoId", grupo);
        String codigo = leer(mvc.perform(post("/api/v1/estudiantes").header("Authorization", colegio.admin())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(datos)))
                .andExpect(status().isCreated()).andReturn()).get("codigo").asText();

        String familiares = "{\"familiares\":["
                + "{\"nombres\":\"Luz\",\"parentesco\":\"MADRE\",\"celular\":\"3004445566\",\"recibeSms\":true},"
                + "{\"nombres\":\"Pedro\",\"parentesco\":\"PADRE\",\"celular\":\"3007778899\",\"recibeSms\":false}]}";
        mvc.perform(put("/api/v1/estudiantes/" + codigo + "/familiares").header("Authorization", colegio.admin())
                        .contentType(MediaType.APPLICATION_JSON).content(familiares))
                .andExpect(status().isOk());
        return codigo;
    }

    private Long categoria(Colegio colegio) throws Exception {
        return leer(mvc.perform(get("/api/v1/categorias").header("Authorization", colegio.admin())).andReturn())
                .get(0).get("id").asLong();
    }

    private void crearAlerta(Colegio colegio, String docente, String estudiante) throws Exception {

        String body = "{\"estudianteCodigo\":\"" + estudiante + "\",\"categoriaId\":" + categoria(colegio)
                + ",\"nivel\":\"ALTO\",\"descripcion\":\"Consumo de sustancias en el bano\"}";
        mvc.perform(post("/api/v1/alertas").header("Authorization", docente)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    private String agendar(String psicorientador, String estudiante) throws Exception {

        String body = "{\"estudianteCodigo\":\"" + estudiante + "\",\"inicio\":\"" + OffsetDateTime.now().plusDays(1)
                + "\",\"duracionMinutos\":45,\"modalidad\":\"PRESENCIAL\"}";
        return leer(mvc.perform(post("/api/v1/citas").header("Authorization", psicorientador)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn()).get("codigo").asText();
    }

    private List<Map<String, Object>> sms(Colegio colegio) {
        return OWNER.queryForList("SELECT * FROM sms_envios WHERE sms_ins_id = ? ORDER BY sms_id", colegio.id());
    }

    // el envio es en otro hilo: se espera a que quede enviado
    private void esperarEnviados(Colegio colegio) throws Exception {
        for (int i = 0; i < 50; i++) {
            Integer pendientes = OWNER.queryForObject(
                    "SELECT count(*) FROM sms_envios WHERE sms_ins_id = ? AND sms_estado = 'PENDIENTE'", Integer.class, colegio.id());
            if (pendientes == 0) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("los sms siguen pendientes");
    }

    @Test
    void alertaDelDocenteAvisaSoloALaFamiliaSinDetallesNiTildes() throws Exception {

        Colegio colegio = crearColegioCompleto(mvc, mapper, "sms-alerta", "51510000");
        String docente = crearPersonal(colegio, "5151002", Rol.DOCENTE);
        String estudiante = crearEstudiante(colegio, "5151001");

        // con el sms apagado (asi nace el colegio) no sale nada
        crearAlerta(colegio, docente, estudiante);
        assertThat(sms(colegio)).isEmpty();

        smsDelColegio(colegio, true);
        crearAlerta(colegio, docente, estudiante);
        esperarEnviados(colegio);

        List<Map<String, Object>> enviados = sms(colegio);
        // solo la mama (el papa no recibe sms y el estudiante estaba presente)
        assertThat(enviados).hasSize(1);
        Map<String, Object> mensaje = enviados.get(0);
        assertThat(mensaje.get("sms_destinatario")).isEqualTo("FAMILIAR");
        assertThat(mensaje.get("sms_celular")).isEqualTo("3004445566");
        assertThat(mensaje.get("sms_estado")).isEqualTo("ENVIADO");
        String texto = (String) mensaje.get("sms_mensaje");
        assertThat(texto).contains("Jose Pena").contains("Colegio sms-alerta");
        assertThat(texto).doesNotContain("sustancias").doesNotContain("ALTO").doesNotContain("é").doesNotContain("ñ");
    }

    @Test
    void citaAvisaAlEstudianteYALaFamiliaSegunLasReglas() throws Exception {

        Colegio colegio = crearColegioCompleto(mvc, mapper, "sms-cita", "52520000");
        String psicorientador = crearPersonal(colegio, "5252003", Rol.PSICORIENTADOR);
        String estudiante = crearEstudiante(colegio, "5252001");
        smsDelColegio(colegio, true);

        // solicitud propia sin autorizar a la familia: la cita solo le llega al estudiante
        Long usuario = idUsuario(colegio.id(), "5252001");
        OWNER.update("UPDATE usuarios SET usu_debe_cambiar_contrasena = false WHERE usu_id = ?", usuario);
        String tokenEstudiante = token(usuario, colegio.id(), colegio.slug(), Rol.ESTUDIANTE);
        mvc.perform(post("/api/v1/alertas/solicitud-ayuda").header("Authorization", tokenEstudiante)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoriaId\":" + categoria(colegio) + ",\"urgencia\":\"MEDIA\","
                                + "\"descripcion\":\"Necesito hablar con alguien\"}"))
                .andExpect(status().isCreated());
        assertThat(sms(colegio)).isEmpty();

        String cita = agendar(psicorientador, estudiante);
        esperarEnviados(colegio);
        List<Map<String, Object>> enviados = sms(colegio);
        assertThat(enviados).hasSize(1);
        assertThat(enviados.get(0).get("sms_destinatario")).isEqualTo("ESTUDIANTE");
        assertThat(enviados.get(0).get("sms_evento")).isEqualTo("CITA_AGENDADA");
        assertThat((String) enviados.get(0).get("sms_mensaje")).startsWith("Hola Jose Pena, tienes una cita con orientacion escolar el");

        // cancelada: tambien solo al estudiante
        mvc.perform(post("/api/v1/citas/" + cita + "/cancelar").header("Authorization", psicorientador)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Paro\"}"))
                .andExpect(status().isOk());
        esperarEnviados(colegio);
        assertThat(sms(colegio)).hasSize(2);
        assertThat((String) sms(colegio).get(1).get("sms_mensaje")).contains("fue cancelada");

        // con una alerta de docente en la cita, la familia tambien se entera
        String docente = crearPersonal(colegio, "5252002", Rol.DOCENTE);
        crearAlerta(colegio, docente, estudiante);
        esperarEnviados(colegio);
        int antes = sms(colegio).size();
        agendar(psicorientador, estudiante);
        esperarEnviados(colegio);
        List<Map<String, Object>> todos = sms(colegio);
        assertThat(todos).hasSize(antes + 2);
        assertThat(todos.get(todos.size() - 1).get("sms_destinatario")).isEqualTo("FAMILIAR");

        // el psicorientador apaga el sms a la familia de este estudiante: ya no les llega nada
        mvc.perform(patch("/api/v1/estudiantes/" + estudiante + "/sms-familiares").header("Authorization", colegio.admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}"))
                .andExpect(status().isOk());
        int antesDeApagar = sms(colegio).size();
        crearAlerta(colegio, docente, estudiante);
        assertThat(sms(colegio)).hasSize(antesDeApagar);
    }

    @Test
    void cadaInstitucionEnciendeOApagaSuSmsSinAfectarALasDemas() throws Exception {

        Colegio a = crearColegioCompleto(mvc, mapper, "sms-uno", "53530000");
        Colegio b = crearColegioCompleto(mvc, mapper, "sms-dos", "53530001");
        String docenteA = crearPersonal(a, "5353002", Rol.DOCENTE);
        String docenteB = crearPersonal(b, "5353012", Rol.DOCENTE);
        String estudianteA = crearEstudiante(a, "5353001");
        String estudianteB = crearEstudiante(b, "5353011");

        // solo se enciende el primero
        smsDelColegio(a, true);
        Boolean segundo = OWNER.queryForObject("SELECT ins_sms_activo FROM instituciones WHERE ins_id = ?", Boolean.class, b.id());
        assertThat(segundo).isFalse();

        crearAlerta(a, docenteA, estudianteA);
        crearAlerta(b, docenteB, estudianteB);
        esperarEnviados(a);
        assertThat(sms(a)).hasSize(1);
        assertThat(sms(b)).isEmpty();

        // se apaga el primero y se enciende el segundo: ahora es al reves
        smsDelColegio(a, false);
        smsDelColegio(b, true);
        crearAlerta(a, docenteA, estudianteA);
        crearAlerta(b, docenteB, estudianteB);
        esperarEnviados(b);
        assertThat(sms(a)).hasSize(1);
        assertThat(sms(b)).hasSize(1);

        // el admin del colegio no lo cambia: es del superadmin
        mvc.perform(patch("/api/v1/superadmin/instituciones/" + a.slug() + "/sms").header("Authorization", a.admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":true}"))
                .andExpect(status().isForbidden());
    }
}
