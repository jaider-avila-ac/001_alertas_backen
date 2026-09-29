package com.alertas.alerta;

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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

class AlertaTest extends IntegracionTest {

    static final int ESTE_ANIO = LocalDate.now().getYear();

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    private JsonNode leer(MvcResult resultado) throws Exception {
        return mapper.readTree(resultado.getResponse().getContentAsString());
    }

    private JsonNode obtener(String ruta, String token) throws Exception {
        return leer(mvc.perform(get(ruta).header("Authorization", token)).andExpect(status().isOk()).andReturn());
    }

    // colegio con Sexto A, un estudiante matriculado, un docente y la primera categoria
    record Escenario(Colegio colegio, Long grupoId, String estudiante, String docente, Long categoriaId) {
    }

    private Escenario escenario(String slug, String documentoAdmin) throws Exception {

        Colegio colegio = crearColegioCompleto(mvc, mapper, slug, documentoAdmin);
        Long grupo = crearGrupo(colegio, "Sexto", "A");
        String estudiante = crearEstudiante(colegio, documentoAdmin + "1", "Ana", grupo);
        String docente = crearPersonal(colegio, documentoAdmin + "2", Rol.DOCENTE);
        Long categoria = obtener("/api/v1/categorias", colegio.admin()).get(0).get("id").asLong();
        return new Escenario(colegio, grupo, estudiante, docente, categoria);
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

    private String crearEstudiante(Colegio colegio, String documento, String nombres, Long grupoId) throws Exception {

        Map<String, Object> datos = new HashMap<>();
        datos.put("tipoDoc", "TI");
        datos.put("nroDoc", documento);
        datos.put("nombres", nombres);
        datos.put("apellidos", "Prueba");
        datos.put("grupoId", grupoId);
        return leer(mvc.perform(post("/api/v1/estudiantes").header("Authorization", colegio.admin())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(datos)))
                .andExpect(status().isCreated()).andReturn()).get("codigo").asText();
    }

    // devuelve el token de la persona creada
    private String crearPersonal(Colegio colegio, String documento, Rol rol) throws Exception {

        String body = "{\"tipoDoc\":\"CC\",\"nroDoc\":\"" + documento + "\",\"nombres\":\"Persona\",\"apellidos\":\""
                + documento + "\",\"rol\":\"" + rol.name() + "\"}";
        mvc.perform(post("/api/v1/personal").header("Authorization", colegio.admin())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        Long usuario = idUsuario(colegio.id(), documento);
        OWNER.update("UPDATE usuarios SET usu_debe_cambiar_contrasena = false WHERE usu_id = ?", usuario);
        return token(usuario, colegio.id(), colegio.slug(), rol);
    }

    private String tokenEstudiante(Colegio colegio, String documento) {

        Long usuario = idUsuario(colegio.id(), documento);
        OWNER.update("UPDATE usuarios SET usu_debe_cambiar_contrasena = false WHERE usu_id = ?", usuario);
        return token(usuario, colegio.id(), colegio.slug(), Rol.ESTUDIANTE);
    }

    private Map<String, Object> alerta(Escenario e, String nivel) {

        Map<String, Object> datos = new HashMap<>();
        datos.put("estudianteCodigo", e.estudiante());
        datos.put("categoriaId", e.categoriaId());
        datos.put("nivel", nivel);
        datos.put("descripcion", "Se vio llorando en el descanso y no quiso hablar");
        datos.put("fechaHecho", LocalDate.now().toString());
        datos.put("lugar", "Patio");
        return datos;
    }

    private ResultActions crear(String token, Map<String, Object> datos, String llave) throws Exception {

        return mvc.perform(post("/api/v1/alertas").header("Authorization", token)
                .header("Idempotency-Key", llave)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(datos)));
    }

    private ResultActions crear(String token, Map<String, Object> datos) throws Exception {
        return crear(token, datos, UUID.randomUUID().toString());
    }

    @Test
    void elDocenteCreaUnaAlertaQueGuardaElGrupoDelMomento() throws Exception {

        Escenario e = escenario("ale-crear", "61610000");
        String llave = UUID.randomUUID().toString();

        JsonNode creada = leer(crear(e.docente(), alerta(e, "LEVE"), llave)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.origen").value("DOCENTE"))
                .andExpect(jsonPath("$.gradoNombre").value("Sexto"))
                .andExpect(jsonPath("$.grupoNombre").value("A"))
                .andExpect(jsonPath("$.prioritaria").value(false))
                .andExpect(jsonPath("$.psicorientador").isEmpty())
                .andExpect(jsonPath("$.id").doesNotExist())
                .andReturn());

        // doble clic: la misma llave no crea otra
        crear(e.docente(), alerta(e, "LEVE"), llave).andExpect(status().isConflict());
        assertThat(creada.get("codigo").asText()).hasSize(12);
        Integer total = OWNER.queryForObject("SELECT count(*) FROM alertas WHERE ale_ins_id = ?", Integer.class, e.colegio().id());
        assertThat(total).isEqualTo(1);

        Long matricula = OWNER.queryForObject("SELECT ale_mat_id FROM alertas WHERE ale_ins_id = ?", Long.class, e.colegio().id());
        assertThat(matricula).isNotNull();

        // critica o con peligro inmediato: prioritaria
        crear(e.docente(), alerta(e, "CRITICO")).andExpect(jsonPath("$.prioritaria").value(true));
        Map<String, Object> peligro = alerta(e, "MODERADO");
        peligro.put("peligroInmediato", true);
        crear(e.docente(), peligro).andExpect(jsonPath("$.prioritaria").value(true));

        // el grupo cuenta sus alertas
        JsonNode grupos = obtener("/api/v1/grupos", e.colegio().admin());
        assertThat(grupos.get(0).get("totalAlertas").asLong()).isEqualTo(3);
    }

    @Test
    void validaciones() throws Exception {

        Escenario e = escenario("ale-validar", "62620000");

        Map<String, Object> corta = alerta(e, "LEVE");
        corta.put("descripcion", "corta");
        crear(e.docente(), corta).andExpect(status().isBadRequest());

        Map<String, Object> futura = alerta(e, "LEVE");
        futura.put("fechaHecho", LocalDate.now().plusDays(2).toString());
        crear(e.docente(), futura)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La fecha del hecho no puede ser futura"));

        // categoria desactivada
        mvc.perform(patch("/api/v1/categorias/" + e.categoriaId() + "/estado").header("Authorization", e.colegio().admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}"))
                .andExpect(status().isOk());
        crear(e.docente(), alerta(e, "LEVE")).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/v1/categorias/" + e.categoriaId() + "/estado").header("Authorization", e.colegio().admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":true}"))
                .andExpect(status().isOk());

        // retirado: sin matricula activa
        mvc.perform(post("/api/v1/estudiantes/" + e.estudiante() + "/retirar").header("Authorization", e.colegio().admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Se fue\"}"))
                .andExpect(status().isOk());
        crear(e.docente(), alerta(e, "LEVE")).andExpect(status().isConflict());

        // estudiante de otro colegio: no existe
        Escenario otro = escenario("ale-validar-otro", "62620001");
        Map<String, Object> ajena = alerta(e, "LEVE");
        ajena.put("estudianteCodigo", otro.estudiante());
        crear(e.docente(), ajena).andExpect(status().isNotFound());

        // un estudiante no crea alertas de docente
        crear(tokenEstudiante(otro.colegio(), "626200011"), alerta(otro, "LEVE")).andExpect(status().isForbidden());
    }

    @Test
    void misAlertasYQuienVeElDetalle() throws Exception {

        Escenario e = escenario("ale-mias", "63630000");
        String otroDocente = crearPersonal(e.colegio(), "636300003", Rol.DOCENTE);

        String codigo = leer(crear(e.docente(), alerta(e, "ALTO")).andReturn()).get("codigo").asText();
        crear(e.docente(), alerta(e, "LEVE"));
        crear(otroDocente, alerta(e, "LEVE"));

        mvc.perform(get("/api/v1/alertas/mias").header("Authorization", e.docente()))
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.contenido[0].estudianteNombres").value("Ana"));
        mvc.perform(get("/api/v1/alertas/mias").param("tamanio", "1").header("Authorization", e.docente()))
                .andExpect(jsonPath("$.totalPaginas").value(2));
        mvc.perform(get("/api/v1/alertas/mias").param("estado", "COMPLETADA").header("Authorization", e.docente()))
                .andExpect(jsonPath("$.totalElementos").value(0));

        mvc.perform(get("/api/v1/alertas/" + codigo).header("Authorization", e.docente()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nivel").value("ALTO"));
        // otro docente no la ve
        mvc.perform(get("/api/v1/alertas/" + codigo).header("Authorization", otroDocente))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/alertas/" + codigo).header("Authorization", e.colegio().admin()))
                .andExpect(status().isOk());
    }

    @Test
    void elEstudiantePideAyuda() throws Exception {

        Escenario e = escenario("ale-ayuda", "64640000");
        String estudiante = tokenEstudiante(e.colegio(), "646400001");

        String body = "{\"categoriaId\":" + e.categoriaId() + ",\"urgencia\":\"ALTA\","
                + "\"descripcion\":\"Me molestan todos los dias en el bus\",\"horarioSeguro\":\"En el descanso\","
                + "\"modalidadPreferida\":\"PRESENCIAL\"}";

        mvc.perform(post("/api/v1/alertas/solicitud-ayuda").header("Authorization", estudiante)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.origen").value("ESTUDIANTE"))
                .andExpect(jsonPath("$.nivel").value("ALTO"))
                .andExpect(jsonPath("$.prioritaria").value(true))
                .andExpect(jsonPath("$.autorizaSmsFamiliares").value(false))
                .andExpect(jsonPath("$.horarioSeguro").value("En el descanso"));

        // el docente no usa esta ruta
        mvc.perform(post("/api/v1/alertas/solicitud-ayuda").header("Authorization", e.docente())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void laAlertaNuevaLlegaAlPsicorientadorQueYaLoAtiende() throws Exception {

        Escenario e = escenario("ale-asignar", "65650000");
        crearPersonal(e.colegio(), "656500005", Rol.PSICORIENTADOR);

        String primera = leer(crear(e.docente(), alerta(e, "LEVE")).andReturn()).get("codigo").asText();

        // el psicorientador tomo al estudiante (esto lo hace la fase de atencion)
        OWNER.update("""
                UPDATE alertas SET ale_psi_id = (SELECT per_id FROM personal WHERE per_ins_id = ? AND per_nro_doc = '656500005'),
                                   ale_asignada_en = now()
                WHERE ale_ins_id = ? AND ale_codigo = ?""", e.colegio().id(), e.colegio().id(), primera);

        crear(e.docente(), alerta(e, "MODERADO"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.psicorientador").value("Persona 656500005"));
    }
}
