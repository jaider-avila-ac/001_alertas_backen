package com.alertas.matricula;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

class MatriculaTest extends IntegracionTest {

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
        return leer(mvc.perform(post("/api/v1/grupos").header("Authorization", colegio.admin())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
    }

    private void crearAnio(Colegio colegio, int anio) throws Exception {

        mvc.perform(post("/api/v1/anios-lectivos").header("Authorization", colegio.admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"anio\":" + anio + "}"))
                .andExpect(status().isCreated());
    }

    private ResultActions crearEstudiante(Colegio colegio, Map<String, Object> datos) throws Exception {

        return mvc.perform(post("/api/v1/estudiantes").header("Authorization", colegio.admin())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(datos)));
    }

    private String crearYCodigo(Colegio colegio, String documento, Long grupoId) throws Exception {

        Map<String, Object> datos = new HashMap<>();
        datos.put("tipoDoc", "TI");
        datos.put("nroDoc", documento);
        datos.put("nombres", "Estudiante " + documento);
        datos.put("apellidos", "Prueba");
        datos.put("grupoId", grupoId);
        return leer(crearEstudiante(colegio, datos).andExpect(status().isCreated()).andReturn()).get("codigo").asText();
    }

    private ResultActions moverA(Colegio colegio, String codigo, Long grupoId, String motivo) throws Exception {

        String body = "{\"grupoId\":" + grupoId + ",\"motivo\":\"" + motivo + "\"}";
        return mvc.perform(patch("/api/v1/estudiantes/" + codigo + "/grupo").header("Authorization", colegio.admin())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode grupoEnPromocion(JsonNode promocion, Long grupoId) {

        for (JsonNode grupo : promocion.get("grupos")) {
            if (grupo.get("grupoId").asLong() == grupoId) {
                return grupo;
            }
        }
        throw new IllegalStateException("el grupo no esta en la promocion");
    }

    @Test
    void promocionPasaSoloALosActivosYGraduaAlUltimoGrado() throws Exception {

        Colegio colegio = crearColegioCompleto(mvc, mapper, "mat-promocion", "31310000");
        Long sextoA = crearGrupo(colegio, ESTE_ANIO, "Sexto", "A");
        Long sextoB = crearGrupo(colegio, ESTE_ANIO, "Sexto", "B");
        Long onceA = crearGrupo(colegio, ESTE_ANIO, "Once", "A");

        String ana = crearYCodigo(colegio, "3101", sextoA);
        crearYCodigo(colegio, "3102", sextoA);
        crearYCodigo(colegio, "3103", sextoB);
        String inactivo = crearYCodigo(colegio, "3104", sextoB);
        String once = crearYCodigo(colegio, "3105", onceA);

        mvc.perform(patch("/api/v1/estudiantes/" + inactivo + "/estado").header("Authorization", colegio.admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}"))
                .andExpect(status().isOk());

        // sin anio siguiente no hay promocion
        mvc.perform(get("/api/v1/promocion").header("Authorization", colegio.admin()))
                .andExpect(status().isConflict());

        crearAnio(colegio, ESTE_ANIO + 1);

        // faltan: Septimo A y B (vienen de Sexto), Sexto A y B (el grado mas bajo copia los suyos) y Once A
        JsonNode previa = obtener("/api/v1/promocion", colegio.admin());
        assertThat(previa.get("destinoAnio").asInt()).isEqualTo(ESTE_ANIO + 1);
        assertThat(previa.get("gruposPorCrear").size()).isEqualTo(5);

        JsonNode preparada = leer(mvc.perform(post("/api/v1/promocion/preparar-grupos").header("Authorization", colegio.admin()))
                .andExpect(status().isOk()).andReturn());
        assertThat(preparada.get("gruposPorCrear").size()).isZero();
        assertThat(preparada.get("gruposDestino").size()).isEqualTo(5);

        JsonNode grupoA = grupoEnPromocion(preparada, sextoA);
        assertThat(grupoA.get("porPromover").asLong()).isEqualTo(2);
        assertThat(grupoA.get("destinoSugeridoId").isNull()).isFalse();
        // el inactivo no cuenta
        assertThat(grupoEnPromocion(preparada, sextoB).get("porPromover").asLong()).isEqualTo(1);
        assertThat(grupoEnPromocion(preparada, onceA).get("seGradua").asBoolean()).isTrue();

        mvc.perform(post("/api/v1/promocion/confirmar").header("Authorization", colegio.admin())
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.promovidos").value(3))
                .andExpect(jsonPath("$.graduados").value(1));

        // otra vez no duplica
        mvc.perform(post("/api/v1/promocion/confirmar").header("Authorization", colegio.admin())
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(jsonPath("$.promovidos").value(0))
                .andExpect(jsonPath("$.graduados").value(0));

        mvc.perform(get("/api/v1/estudiantes/" + ana).header("Authorization", colegio.admin()))
                .andExpect(jsonPath("$.trayectoria.length()").value(2))
                .andExpect(jsonPath("$.trayectoria[0].gradoNombre").value("Septimo"))
                .andExpect(jsonPath("$.trayectoria[0].grupoNombre").value("A"))
                .andExpect(jsonPath("$.trayectoria[0].origen").value("PROMOCION"))
                .andExpect(jsonPath("$.trayectoria[1].estado").value("PROMOVIDA"))
                // sigue en Sexto A hasta que se active el anio nuevo
                .andExpect(jsonPath("$.matriculaActual.grupoNombre").value("A"))
                .andExpect(jsonPath("$.matriculaActual.gradoNombre").value("Sexto"));

        mvc.perform(get("/api/v1/estudiantes/" + inactivo).header("Authorization", colegio.admin()))
                .andExpect(jsonPath("$.trayectoria.length()").value(1))
                .andExpect(jsonPath("$.trayectoria[0].estado").value("ACTIVA"));

        mvc.perform(get("/api/v1/estudiantes/" + once).header("Authorization", colegio.admin()))
                .andExpect(jsonPath("$.trayectoria[0].estado").value("GRADUADA"));

        // Ana repite: se pasa a Sexto del anio nuevo
        Long sextoNuevo = null;
        for (JsonNode grupo : preparada.get("gruposDestino")) {
            if (grupo.get("gradoNombre").asText().equals("Sexto") && grupo.get("nombre").asText().equals("A")) {
                sextoNuevo = grupo.get("id").asLong();
            }
        }

        moverA(colegio, ana, sextoNuevo, "Perdio el anio")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trayectoria[0].origen").value("REPITE"))
                .andExpect(jsonPath("$.trayectoria[0].movimientos[0].motivo").value("Perdio el anio"))
                .andExpect(jsonPath("$.trayectoria[1].estado").value("REPROBADA"));
    }

    @Test
    void retirarCierraLaMatriculaYReingresarLaAbre() throws Exception {

        Colegio colegio = crearColegioCompleto(mvc, mapper, "mat-retiro", "32320000");
        Long octavoA = crearGrupo(colegio, ESTE_ANIO, "Octavo", "A");
        String codigo = crearYCodigo(colegio, "3201", octavoA);

        mvc.perform(post("/api/v1/estudiantes/" + codigo + "/retirar").header("Authorization", colegio.admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/v1/estudiantes/" + codigo + "/retirar").header("Authorization", colegio.admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Se cambio de ciudad\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false))
                .andExpect(jsonPath("$.matriculaActual.estado").value("RETIRADA"))
                .andExpect(jsonPath("$.matriculaActual.motivoCierre").value("Se cambio de ciudad"))
                .andExpect(jsonPath("$.matriculaActual.fechaCierre").isNotEmpty());

        // no cuenta en el grupo
        JsonNode grupos = obtener("/api/v1/grupos", colegio.admin());
        assertThat(grupos.get(0).get("totalEstudiantes").asLong()).isZero();

        // otra vez no: ya no tiene matricula activa
        mvc.perform(post("/api/v1/estudiantes/" + codigo + "/retirar").header("Authorization", colegio.admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"otra vez\"}"))
                .andExpect(status().isConflict());

        // vuelve en el mismo anio
        moverA(colegio, codigo, octavoA, "Regreso")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(true))
                .andExpect(jsonPath("$.matriculaActual.estado").value("ACTIVA"))
                .andExpect(jsonPath("$.trayectoria.length()").value(1));
    }

    @Test
    void unGrupoPorElQuePasaronEstudiantesNoSeBorra() throws Exception {

        Colegio colegio = crearColegioCompleto(mvc, mapper, "mat-historial", "33330000");
        Long a = crearGrupo(colegio, ESTE_ANIO, "Noveno", "A");
        Long b = crearGrupo(colegio, ESTE_ANIO, "Noveno", "B");
        String codigo = crearYCodigo(colegio, "3301", a);

        moverA(colegio, codigo, b, "Cambio de jornada").andExpect(status().isOk());

        // A quedo vacio, pero el movimiento lo menciona
        mvc.perform(delete("/api/v1/grupos/" + a).header("Authorization", colegio.admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Por el grupo ya pasaron estudiantes, queda como historial y no se puede borrar"));
    }

    @Test
    void datosDeSaludYContacto() throws Exception {

        Colegio colegio = crearColegioCompleto(mvc, mapper, "mat-salud", "34340000");
        Long grupo = crearGrupo(colegio, ESTE_ANIO, "Quinto", "A");

        Map<String, Object> datos = new HashMap<>();
        datos.put("tipoDoc", "TI");
        datos.put("nroDoc", "3401");
        datos.put("nombres", "Sara");
        datos.put("apellidos", "Mesa");
        datos.put("grupoId", grupo);
        datos.put("eps", "Nueva EPS");
        datos.put("rh", "O+");
        datos.put("condicionesSalud", "Alergica a la penicilina");
        datos.put("direccion", "Calle 10 # 5-20");
        datos.put("barrio", "Centro");
        datos.put("correo", "sara@correo.com");

        crearEstudiante(colegio, datos)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eps").value("Nueva EPS"))
                .andExpect(jsonPath("$.rh").value("O+"))
                .andExpect(jsonPath("$.condicionesSalud").value("Alergica a la penicilina"))
                .andExpect(jsonPath("$.barrio").value("Centro"));

        datos.put("nroDoc", "3402");
        datos.put("rh", "Z+");
        crearEstudiante(colegio, datos)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El RH no es valido"));
    }
}
