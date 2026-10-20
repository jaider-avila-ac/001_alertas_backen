package com.alertas.estudiante;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alertas.auth.model.Rol;
import com.alertas.soporte.IntegracionTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

class EstudianteTest extends IntegracionTest {

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

    private ResultActions crearEstudiante(Colegio colegio, String documento, String nombres, String grupo) throws Exception {

        String body = mapper.writeValueAsString(Map.of(
                "tipoDoc", "TI",
                "nroDoc", documento,
                "nombres", nombres,
                "apellidos", "Pérez",
                "grupo", grupo));

        return mvc.perform(post("/api/v1/estudiantes").header("Authorization", colegio.admin())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String crearYCodigo(Colegio colegio, String documento, String nombres, String grupo) throws Exception {
        return leer(crearEstudiante(colegio, documento, nombres, grupo).andExpect(status().isCreated()).andReturn())
                .get("codigo").asText();
    }

    private String tokenDocente(Colegio colegio, String documento) {

        Long docente = crearUsuario(colegio.id(), documento, Rol.DOCENTE, "x", false);
        return token(docente, colegio.id(), colegio.slug(), Rol.DOCENTE);
    }

    @Test
    void crearEstudianteConSuGrupoYSinIds() throws Exception {

        Colegio colegio = crearColegioCompleto(mvc, mapper, "est-crear", "11110000");
        String sextoA = "Sexto A";

        JsonNode creado = leer(crearEstudiante(colegio, "1001", "José", sextoA)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.grupo").value("Sexto A"))
                .andExpect(jsonPath("$.debeCambiarContrasena").value(true))
                .andReturn());

        assertThat(creado.get("codigo").asText()).hasSize(12);
        assertThat(creado.has("id")).isFalse();

        // documento repetido, y el documento del admin
        crearEstudiante(colegio, "1001", "Otro", sextoA).andExpect(status().isConflict());
        crearEstudiante(colegio, "11110000", "Otro", sextoA).andExpect(status().isConflict());

        // el grupo es texto libre, pero sin caracteres raros
        crearEstudiante(colegio, "1002", "Ana", "<b>6A</b>").andExpect(status().isBadRequest());
        crearEstudiante(colegio, "1003", "Ana", "Ing. Sistemas - Semestre 3").andExpect(status().isCreated());
    }

    @Test
    void laFechaDeNacimientoDebeDarUnaEdadPosible() throws Exception {

        Colegio colegio = crearColegioCompleto(mvc, mapper, "est-nacimiento", "11120000");
        String sextoA = "Sexto A";

        // 1600, futura o de un bebe: no
        for (String fecha : new String[] {"1600-05-10", LocalDate.now().plusDays(3).toString(), LocalDate.now().minusYears(1).toString()}) {
            String body = mapper.writeValueAsString(Map.of("tipoDoc", "TI", "nroDoc", "1201", "nombres", "Ana",
                    "apellidos", "Ruiz", "grupo", sextoA, "fechaNacimiento", fecha));
            mvc.perform(post("/api/v1/estudiantes").header("Authorization", colegio.admin())
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("fecha de nacimiento")));
        }

        // 12 anios: si (tambien un adulto, la plataforma sirve para universidades)
        String valida = LocalDate.now().minusYears(12).minusDays(10).toString();
        String body = mapper.writeValueAsString(Map.of("tipoDoc", "TI", "nroDoc", "1201", "nombres", "Ana",
                "apellidos", "Ruiz", "grupo", sextoA, "fechaNacimiento", valida));
        mvc.perform(post("/api/v1/estudiantes").header("Authorization", colegio.admin())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fechaNacimiento").value(valida));

        // la base tampoco acepta una fecha absurda aunque alguien se salte el backend
        Long id = OWNER.queryForObject("SELECT est_id FROM estudiantes WHERE est_ins_id = ? AND est_nro_doc = '1201'",
                Long.class, colegio.id());
        try {
            OWNER.update("UPDATE estudiantes SET est_fecha_nacimiento = '1600-01-01' WHERE est_id = ?", id);
            throw new AssertionError("la base acepto 1600");
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            assertThat(e.getMessage()).contains("ck_estudiantes_fecha_nacimiento");
        }
    }

    @Test
    void listarBuscaFiltraYPagina() throws Exception {

        Colegio colegio = crearColegioCompleto(mvc, mapper, "est-listar", "22220000");
        String sextoA = "Sexto A";
        String septimoA = "Septimo A";

        crearYCodigo(colegio, "2001", "José", sextoA);
        crearYCodigo(colegio, "2002", "Ana", sextoA);
        String inactivo = crearYCodigo(colegio, "2003", "Luis", septimoA);

        mvc.perform(patch("/api/v1/estudiantes/" + inactivo + "/estado").header("Authorization", colegio.admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}"))
                .andExpect(status().isOk());

        // sin tildes y en mayusculas
        mvc.perform(get("/api/v1/estudiantes").param("texto", "JOSE").header("Authorization", colegio.admin()))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].grupo").value("Sexto A"));

        mvc.perform(get("/api/v1/estudiantes").param("grupo", "sexto a").header("Authorization", colegio.admin()))
                .andExpect(jsonPath("$.totalElementos").value(2));

        // por rango de edad: ninguno tiene fecha de nacimiento
        mvc.perform(get("/api/v1/estudiantes").param("edad", "SIN_FECHA").header("Authorization", colegio.admin()))
                .andExpect(jsonPath("$.totalElementos").value(2));
        mvc.perform(get("/api/v1/estudiantes").param("edad", "DE_12_A_14").header("Authorization", colegio.admin()))
                .andExpect(jsonPath("$.totalElementos").value(0));

        // los grupos que se han escrito, para los filtros
        mvc.perform(get("/api/v1/estudiantes/grupos").header("Authorization", colegio.admin()))
                .andExpect(jsonPath("$.length()").value(2));

        mvc.perform(get("/api/v1/estudiantes").param("estado", "inactivos").header("Authorization", colegio.admin()))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].nombres").value("Luis"));

        mvc.perform(get("/api/v1/estudiantes").param("estado", "todos").param("tamanio", "2")
                        .header("Authorization", colegio.admin()))
                .andExpect(jsonPath("$.totalElementos").value(3))
                .andExpect(jsonPath("$.totalPaginas").value(2));

        // el docente busca pero no ve el detalle (tiene datos de la familia)
        String docente = tokenDocente(colegio, "2900");
        mvc.perform(get("/api/v1/estudiantes").header("Authorization", docente)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/estudiantes/" + inactivo).header("Authorization", docente)).andExpect(status().isForbidden());
    }

    @Test
    void familiaresMaximoTresYConCelularParaSms() throws Exception {

        Colegio colegio = crearColegioCompleto(mvc, mapper, "est-familia", "33330000");
        String codigo = crearYCodigo(colegio, "3001", "Ana", "Octavo A");
        String ruta = "/api/v1/estudiantes/" + codigo + "/familiares";

        Map<String, Object> madre = Map.of("nombres", "Luz", "parentesco", "MADRE", "celular", "3001234567", "recibeSms", true);
        Map<String, Object> padre = Map.of("nombres", "Juan", "parentesco", "PADRE", "recibeSms", false);
        Map<String, Object> abuela = Map.of("nombres", "Rosa", "parentesco", "ABUELO", "recibeSms", false);

        mvc.perform(put(ruta).header("Authorization", colegio.admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("familiares", List.of(madre, padre, abuela)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.familiares.length()").value(3))
                .andExpect(jsonPath("$.familiares[0].posicion").value(1));

        mvc.perform(put(ruta).header("Authorization", colegio.admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("familiares", List.of(madre, padre, abuela, padre)))))
                .andExpect(status().isBadRequest());

        Map<String, Object> sinCelular = Map.of("nombres", "Pedro", "parentesco", "TIO", "recibeSms", true);
        mvc.perform(put(ruta).header("Authorization", colegio.admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("familiares", List.of(sinCelular)))))
                .andExpect(status().isBadRequest());

        // el psicorientador tambien los maneja y puede apagar los sms a la familia
        Long psico = crearUsuario(colegio.id(), "3900", Rol.PSICORIENTADOR, "x", false);
        String tokenPsico = token(psico, colegio.id(), colegio.slug(), Rol.PSICORIENTADOR);

        mvc.perform(put(ruta).header("Authorization", tokenPsico).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("familiares", List.of(madre)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.familiares.length()").value(1));

        mvc.perform(patch("/api/v1/estudiantes/" + codigo + "/sms-familiares").header("Authorization", tokenPsico)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.smsFamiliares").value(false));
    }

    @Test
    void cambiarDeGrupoEsEditarElTexto() throws Exception {

        Colegio colegio = crearColegioCompleto(mvc, mapper, "est-grupo", "44440000");
        String codigo = crearYCodigo(colegio, "4001", "Ana", "Sexto A");

        String body = mapper.writeValueAsString(Map.of("tipoDoc", "TI", "nroDoc", "4001", "nombres", "Ana",
                "apellidos", "Pérez", "grupo", "Septimo A"));
        mvc.perform(put("/api/v1/estudiantes/" + codigo).header("Authorization", colegio.admin())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grupo").value("Septimo A"));
    }

    @Test
    void qrParaImprimirPorRangoDeEdadYPorPaginas() throws Exception {

        Colegio colegio = crearColegioCompleto(mvc, mapper, "est-qr-edad", "55560000");
        crearYCodigo(colegio, "5101", "Ana", "Sexto A");
        crearYCodigo(colegio, "5102", "Beto", "Sexto A");
        OWNER.update("UPDATE estudiantes SET est_fecha_nacimiento = current_date - interval '13 years' WHERE est_nro_doc = '5101' AND est_ins_id = ?",
                colegio.id());

        // sin rango salen todos los activos, por paginas (antes fallaba sin grupo)
        JsonNode todos = obtener("/api/v1/estudiantes/qr", colegio.admin());
        assertThat(todos.get("contenido")).hasSize(2);
        assertThat(todos.get("contenido").get(0).get("enlace").asText()).contains("/est-qr-edad/q/");

        JsonNode adolescentes = obtener("/api/v1/estudiantes/qr?edad=DE_12_A_14", colegio.admin());
        assertThat(adolescentes.get("contenido")).hasSize(1);
        assertThat(adolescentes.get("contenido").get(0).get("nombres").asText()).isEqualTo("Ana");

        assertThat(obtener("/api/v1/estudiantes/qr?edad=SIN_FECHA", colegio.admin()).get("contenido")).hasSize(1);

        // solo salen los rangos que tienen estudiantes, en orden
        JsonNode rangos = obtener("/api/v1/estudiantes/qr/rangos", colegio.admin());
        assertThat(rangos).hasSize(2);
        assertThat(rangos.get(0).get("clave").asText()).isEqualTo("DE_12_A_14");
        assertThat(rangos.get(0).get("total").asLong()).isEqualTo(1);
        assertThat(rangos.get(1).get("clave").asText()).isEqualTo("SIN_FECHA");
    }

    @Test
    void qrLlevaSoloElCodigoYSePuedeRegenerar() throws Exception {

        Colegio colegio = crearColegioCompleto(mvc, mapper, "est-qr", "55550000");
        String codigo = crearYCodigo(colegio, "5001", "Ana", "Noveno A");

        JsonNode qr = obtener("/api/v1/estudiantes/" + codigo + "/qr", colegio.admin());
        String enlace = qr.get("enlace").asText();
        String codigoQr = enlace.substring(enlace.lastIndexOf('/') + 1);

        assertThat(enlace).startsWith("http://localhost:5173/est-qr/q/");
        assertThat(enlace).doesNotContain("5001");
        assertThat(codigoQr).isNotEqualTo(codigo);

        String docente = tokenDocente(colegio, "5900");
        mvc.perform(get("/api/v1/estudiantes/por-qr/" + codigoQr).header("Authorization", docente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value(codigo))
                .andExpect(jsonPath("$.grupo").value("Noveno A"));

        // un docente de otro colegio no encuentra ese qr
        Colegio otro = crearColegioCompleto(mvc, mapper, "est-qr-otro", "55550001");
        mvc.perform(get("/api/v1/estudiantes/por-qr/" + codigoQr).header("Authorization", tokenDocente(otro, "5901")))
                .andExpect(status().isNotFound());

        // el estudiante no usa esto
        Long idEst = idUsuario(colegio.id(), "5001");
        OWNER.update("UPDATE usuarios SET usu_debe_cambiar_contrasena = false WHERE usu_id = ?", idEst);
        mvc.perform(get("/api/v1/estudiantes/por-qr/" + codigoQr)
                        .header("Authorization", token(idEst, colegio.id(), colegio.slug(), Rol.ESTUDIANTE)))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/estudiantes/" + codigo + "/qr/regenerar").header("Authorization", colegio.admin()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/estudiantes/por-qr/" + codigoQr).header("Authorization", docente))
                .andExpect(status().isNotFound());
    }

    @Test
    void inactivarPorGrupoOPorSeleccion() throws Exception {

        Colegio colegio = crearColegioCompleto(mvc, mapper, "est-masivo", "66660000");
        String decimoA = "Decimo A";
        String onceA = "Once A";
        String uno = crearYCodigo(colegio, "6001", "Ana", decimoA);
        crearYCodigo(colegio, "6002", "Luis", decimoA);
        crearYCodigo(colegio, "6003", "Pedro", onceA);

        Long usuarioUno = idUsuario(colegio.id(), "6001");
        String tokenUno = token(usuarioUno, colegio.id(), colegio.slug(), Rol.ESTUDIANTE);
        Thread.sleep(5);

        mvc.perform(patch("/api/v1/estudiantes/estado-masivo").header("Authorization", colegio.admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"grupo\":\"" + decimoA + "\",\"activo\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.afectados").value(2));

        mvc.perform(get("/api/v1/auth/yo").header("Authorization", tokenUno)).andExpect(status().isUnauthorized());

        mvc.perform(patch("/api/v1/estudiantes/estado-masivo").header("Authorization", colegio.admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"codigos\":[\"" + uno + "\"],\"activo\":true}"))
                .andExpect(jsonPath("$.afectados").value(1));

    }

    @Test
    void elEstudianteVeSuNombreEnElPerfil() throws Exception {

        Colegio colegio = crearColegioCompleto(mvc, mapper, "est-perfil", "77770000");
        crearYCodigo(colegio, "7001", "Valentina", "Quinto A");

        mvc.perform(post("/api/v1/public/est-perfil/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"7001\",\"contrasena\":\"7001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perfil.nombres").value("Valentina"))
                .andExpect(jsonPath("$.perfil.debeCambiarContrasena").value(true));
    }
}
