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

    private Long idDelGrado(Colegio colegio, String nombre) throws Exception {

        for (JsonNode grado : obtener("/api/v1/grados", colegio.admin())) {
            if (grado.get("nombre").asText().equals(nombre)) {
                return grado.get("id").asLong();
            }
        }
        throw new IllegalStateException("no existe el grado " + nombre);
    }

    private Long idDelAnio(Colegio colegio, int anio) throws Exception {

        for (JsonNode fila : obtener("/api/v1/anios-lectivos", colegio.admin())) {
            if (fila.get("anio").asInt() == anio) {
                return fila.get("id").asLong();
            }
        }
        throw new IllegalStateException("no existe el anio " + anio);
    }

    private Long crearGrupo(Colegio colegio, int anio, String grado, String nombre) throws Exception {

        String body = "{\"anioId\":" + idDelAnio(colegio, anio) + ",\"gradoId\":" + idDelGrado(colegio, grado)
                + ",\"nombre\":\"" + nombre + "\"}";
        JsonNode grupo = leer(mvc.perform(post("/api/v1/grupos").header("Authorization", colegio.admin())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn());
        return grupo.get("id").asLong();
    }

    private ResultActions crearEstudiante(Colegio colegio, String documento, String nombres, Long grupoId) throws Exception {

        String body = mapper.writeValueAsString(Map.of(
                "tipoDoc", "TI",
                "nroDoc", documento,
                "nombres", nombres,
                "apellidos", "Pérez",
                "grupoId", grupoId));

        return mvc.perform(post("/api/v1/estudiantes").header("Authorization", colegio.admin())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String crearYCodigo(Colegio colegio, String documento, String nombres, Long grupoId) throws Exception {
        return leer(crearEstudiante(colegio, documento, nombres, grupoId).andExpect(status().isCreated()).andReturn())
                .get("codigo").asText();
    }

    private String tokenDocente(Colegio colegio, String documento) {

        Long docente = crearUsuario(colegio.id(), documento, Rol.DOCENTE, "x", false);
        return token(docente, colegio.id(), colegio.slug(), Rol.DOCENTE);
    }

    @Test
    void crearEstudianteConSuGrupoYSinIds() throws Exception {

        Colegio colegio = crearColegioCompleto(mvc, mapper, "est-crear", "11110000");
        Long sextoA = crearGrupo(colegio, ESTE_ANIO, "Sexto", "A");

        JsonNode creado = leer(crearEstudiante(colegio, "1001", "José", sextoA)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.matriculaActual.gradoNombre").value("Sexto"))
                .andExpect(jsonPath("$.matriculaActual.grupoNombre").value("A"))
                .andExpect(jsonPath("$.debeCambiarContrasena").value(true))
                .andReturn());

        assertThat(creado.get("codigo").asText()).hasSize(12);
        assertThat(creado.has("id")).isFalse();

        // documento repetido, y el documento del admin
        crearEstudiante(colegio, "1001", "Otro", sextoA).andExpect(status().isConflict());
        crearEstudiante(colegio, "11110000", "Otro", sextoA).andExpect(status().isConflict());

        // grupo de otro colegio
        Colegio otro = crearColegioCompleto(mvc, mapper, "est-crear-otro", "11110001");
        Long grupoAjeno = crearGrupo(otro, ESTE_ANIO, "Sexto", "A");
        crearEstudiante(colegio, "1002", "Ana", grupoAjeno).andExpect(status().isNotFound());
    }

    @Test
    void listarBuscaFiltraYPagina() throws Exception {

        Colegio colegio = crearColegioCompleto(mvc, mapper, "est-listar", "22220000");
        Long sextoA = crearGrupo(colegio, ESTE_ANIO, "Sexto", "A");
        Long septimoA = crearGrupo(colegio, ESTE_ANIO, "Septimo", "A");

        crearYCodigo(colegio, "2001", "José", sextoA);
        crearYCodigo(colegio, "2002", "Ana", sextoA);
        String inactivo = crearYCodigo(colegio, "2003", "Luis", septimoA);

        mvc.perform(patch("/api/v1/estudiantes/" + inactivo + "/estado").header("Authorization", colegio.admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}"))
                .andExpect(status().isOk());

        // sin tildes y en mayusculas
        mvc.perform(get("/api/v1/estudiantes").param("texto", "JOSE").header("Authorization", colegio.admin()))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].gradoNombre").value("Sexto"));

        mvc.perform(get("/api/v1/estudiantes").param("grupoId", String.valueOf(sextoA)).header("Authorization", colegio.admin()))
                .andExpect(jsonPath("$.totalElementos").value(2));

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
        String codigo = crearYCodigo(colegio, "3001", "Ana", crearGrupo(colegio, ESTE_ANIO, "Octavo", "A"));
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
    void cambiarDeGrupoGuardaElHistorialPorAnio() throws Exception {

        Colegio colegio = crearColegioCompleto(mvc, mapper, "est-historial", "44440000");
        Long sextoA = crearGrupo(colegio, ESTE_ANIO, "Sexto", "A");
        Long sextoB = crearGrupo(colegio, ESTE_ANIO, "Sexto", "B");
        String codigo = crearYCodigo(colegio, "4001", "Ana", sextoA);

        mvc.perform(patch("/api/v1/estudiantes/" + codigo + "/grupo").header("Authorization", colegio.admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"grupoId\":" + sextoB + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matriculaActual.grupoNombre").value("B"))
                .andExpect(jsonPath("$.trayectoria.length()").value(1))
                .andExpect(jsonPath("$.trayectoria[0].movimientos[0].grupoAnterior").value("A"))
                .andExpect(jsonPath("$.trayectoria[0].movimientos[0].grupoNuevo").value("B"));

        // lo ubica ya en el anio siguiente: su grupo actual no cambia hasta que se active ese anio
        mvc.perform(post("/api/v1/anios-lectivos").header("Authorization", colegio.admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"anio\":" + (ESTE_ANIO + 1) + "}"))
                .andExpect(status().isCreated());
        Long septimoA = crearGrupo(colegio, ESTE_ANIO + 1, "Septimo", "A");

        mvc.perform(patch("/api/v1/estudiantes/" + codigo + "/grupo").header("Authorization", colegio.admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"grupoId\":" + septimoA + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matriculaActual.grupoNombre").value("B"))
                .andExpect(jsonPath("$.trayectoria.length()").value(2))
                .andExpect(jsonPath("$.trayectoria[0].anio").value(ESTE_ANIO + 1))
                .andExpect(jsonPath("$.trayectoria[0].origen").value("PROMOCION"))
                .andExpect(jsonPath("$.trayectoria[1].estado").value("PROMOVIDA"));
    }

    @Test
    void qrLlevaSoloElCodigoYSePuedeRegenerar() throws Exception {

        Colegio colegio = crearColegioCompleto(mvc, mapper, "est-qr", "55550000");
        String codigo = crearYCodigo(colegio, "5001", "Ana", crearGrupo(colegio, ESTE_ANIO, "Noveno", "A"));

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
                .andExpect(jsonPath("$.gradoNombre").value("Noveno"));

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
    void inactivarPorGrupoYLosQueQuedaronSinGrupo() throws Exception {

        Colegio colegio = crearColegioCompleto(mvc, mapper, "est-masivo", "66660000");
        Long decimoA = crearGrupo(colegio, ESTE_ANIO, "Decimo", "A");
        Long onceA = crearGrupo(colegio, ESTE_ANIO, "Once", "A");
        String uno = crearYCodigo(colegio, "6001", "Ana", decimoA);
        crearYCodigo(colegio, "6002", "Luis", decimoA);
        crearYCodigo(colegio, "6003", "Pedro", onceA);

        Long usuarioUno = idUsuario(colegio.id(), "6001");
        String tokenUno = token(usuarioUno, colegio.id(), colegio.slug(), Rol.ESTUDIANTE);
        Thread.sleep(5);

        mvc.perform(patch("/api/v1/estudiantes/estado-masivo").header("Authorization", colegio.admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"grupoId\":" + decimoA + ",\"activo\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.afectados").value(2));

        mvc.perform(get("/api/v1/auth/yo").header("Authorization", tokenUno)).andExpect(status().isUnauthorized());

        mvc.perform(patch("/api/v1/estudiantes/estado-masivo").header("Authorization", colegio.admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"codigos\":[\"" + uno + "\"],\"activo\":true}"))
                .andExpect(jsonPath("$.afectados").value(1));

        // anio siguiente: solo Ana queda ubicada, los otros activos quedan sin grupo
        mvc.perform(post("/api/v1/anios-lectivos").header("Authorization", colegio.admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"anio\":" + (ESTE_ANIO + 1) + "}"))
                .andExpect(status().isCreated());
        Long siguiente = idDelAnio(colegio, ESTE_ANIO + 1);
        Long onceNuevo = crearGrupo(colegio, ESTE_ANIO + 1, "Once", "A");

        mvc.perform(patch("/api/v1/estudiantes/" + uno + "/grupo").header("Authorization", colegio.admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"grupoId\":" + onceNuevo + "}"))
                .andExpect(status().isOk());

        // Pedro (activo, sin grupo en el anio siguiente). Luis ya estaba inactivo
        mvc.perform(get("/api/v1/estudiantes/sin-grupo/total").param("anioId", String.valueOf(siguiente))
                        .header("Authorization", colegio.admin()))
                .andExpect(jsonPath("$.total").value(1));

        mvc.perform(post("/api/v1/estudiantes/sin-grupo/inactivar").header("Authorization", colegio.admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"anioId\":" + siguiente + "}"))
                .andExpect(jsonPath("$.afectados").value(1));
    }

    @Test
    void elEstudianteVeSuNombreEnElPerfil() throws Exception {

        Colegio colegio = crearColegioCompleto(mvc, mapper, "est-perfil", "77770000");
        crearYCodigo(colegio, "7001", "Valentina", crearGrupo(colegio, ESTE_ANIO, "Quinto", "A"));

        mvc.perform(post("/api/v1/public/est-perfil/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"7001\",\"contrasena\":\"7001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perfil.nombres").value("Valentina"))
                .andExpect(jsonPath("$.perfil.debeCambiarContrasena").value(true));
    }
}
