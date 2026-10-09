package com.alertas.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alertas.auth.model.Rol;
import com.alertas.soporte.IntegracionTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;

// recorre todos los endpoints que tiene la aplicacion (no una lista escrita a mano):
// sin sesion, con el rol equivocado y con los codigos de otro colegio
class SeguridadEndpointsTest extends IntegracionTest {

    static final int ESTE_ANIO = LocalDate.now().getYear();

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mapeos;

    // una ruta con su metodo http
    record Ruta(HttpMethod metodo, String patron) {
    }

    private List<Ruta> rutas() {

        List<Ruta> lista = new ArrayList<>();

        for (Map.Entry<RequestMappingInfo, HandlerMethod> entrada : mapeos.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = entrada.getKey();

            // los controladores de apoyo de las pruebas no cuentan
            if (entrada.getValue().getBeanType().getSimpleName().equals("PruebaController")) {
                continue;
            }
            if (info.getPathPatternsCondition() == null) {
                continue;
            }

            Set<RequestMethod> metodos = info.getMethodsCondition().getMethods();
            for (PathPattern patron : info.getPathPatternsCondition().getPatterns()) {
                if (metodos.isEmpty()) {
                    lista.add(new Ruta(HttpMethod.GET, patron.getPatternString()));
                }
                for (RequestMethod metodo : metodos) {
                    lista.add(new Ruta(HttpMethod.valueOf(metodo.name()), patron.getPatternString()));
                }
            }
        }
        return lista;
    }

    private boolean esPublica(String patron) {

        if (patron.startsWith("/api/v1/public/")) {
            return true;
        }
        if (patron.equals("/api/v1/superadmin/auth/login")) {
            return true;
        }
        if (patron.startsWith("/actuator") || patron.equals("/error")) {
            return true;
        }
        return false;
    }

    // cambia cada {variable} de la ruta por el valor dado
    private String armar(String patron, String valor) {
        return patron.replaceAll("\\{[^}]+}", valor);
    }

    private MvcResult llamar(Ruta ruta, String url, String token) throws Exception {

        MockHttpServletRequestBuilder peticion = request(ruta.metodo(), url);
        if (token != null) {
            peticion.header("Authorization", token);
        }
        if (ruta.metodo() != HttpMethod.GET && ruta.metodo() != HttpMethod.DELETE) {
            peticion.header("Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON).content("{}");
        }
        return mvc.perform(peticion).andReturn();
    }

    @Test
    void hayEndpointsParaRevisar() {

        // si esto falla, el recorrido no esta leyendo los controladores
        assertThat(rutas().size()).isGreaterThan(60);
    }

    @Test
    void sinSesionTodoLoQueNoEsPublicoResponde401() throws Exception {

        List<String> fallas = new ArrayList<>();

        for (Ruta ruta : rutas()) {
            if (esPublica(ruta.patron())) {
                continue;
            }
            MvcResult resultado = llamar(ruta, armar(ruta.patron(), "abc123abc123"), null);
            if (resultado.getResponse().getStatus() != 401) {
                fallas.add(ruta.metodo() + " " + ruta.patron() + " -> " + resultado.getResponse().getStatus());
            }
        }

        assertThat(fallas).isEmpty();
    }

    @Test
    void unColegioNoEntraAlSuperadminYElSuperadminNoEntraAUnColegio() throws Exception {

        Colegio colegio = crearColegioCompleto(mvc, mapper, "seg-roles", "71710000");
        String superadmin = token(1L, null, null, Rol.SUPERADMIN);
        List<String> fallas = new ArrayList<>();

        for (Ruta ruta : rutas()) {
            if (esPublica(ruta.patron()) || !ruta.patron().startsWith("/api/v1/")) {
                continue;
            }
            String url = armar(ruta.patron(), "abc123abc123");

            if (ruta.patron().startsWith("/api/v1/superadmin/")) {
                int codigo = llamar(ruta, url, colegio.admin()).getResponse().getStatus();
                if (codigo != 403) {
                    fallas.add("admin de colegio en " + ruta.metodo() + " " + ruta.patron() + " -> " + codigo);
                }
            } else {
                int codigo = llamar(ruta, url, superadmin).getResponse().getStatus();
                if (codigo != 403) {
                    fallas.add("superadmin en " + ruta.metodo() + " " + ruta.patron() + " -> " + codigo);
                }
            }
        }

        assertThat(fallas).isEmpty();
    }

    @Test
    void conLosCodigosDeOtroColegioNadaFuncionaNiRevientaNiCambia() throws Exception {

        // colegio A: el que intenta. colegio B: el dueno de los datos
        Colegio a = crearColegioCompleto(mvc, mapper, "seg-a", "72720000");
        String psicorientadorA = crearPersonal(a, "727200001", Rol.PSICORIENTADOR);
        String docenteA = crearPersonal(a, "727200002", Rol.DOCENTE);

        Colegio b = crearColegioCompleto(mvc, mapper, "seg-b", "73730000");
        Long anioB = anioActivo(b);
        Long gradoB = primerGrado(b);
        Long grupoB = crearGrupo(b, anioB, gradoB);
        String estudianteB = crearEstudiante(b, "737300001", grupoB);
        String docenteB = crearPersonal(b, "737300002", Rol.DOCENTE);
        String alertaB = crearAlerta(b, docenteB, estudianteB);
        String personalB = obtener("/api/v1/personal", b.admin()).get("contenido").get(0).get("codigo").asText();

        esperarNotificaciones();
        Long categoriaB = obtener("/api/v1/categorias", b.admin()).get(0).get("id").asLong();
        Long notificacionB = OWNER.queryForObject(
                "SELECT min(not_id) FROM notificaciones WHERE not_ins_id = ?", Long.class, b.id());
        String qrB = OWNER.queryForObject(
                "SELECT est_codigo_qr FROM estudiantes WHERE est_ins_id = ?", String.class, b.id());

        // los codigos son aleatorios y no se repiten entre tablas: se prueban todos en cada ruta.
        // los ids numericos si se repiten entre tablas (el grupo 5 y la categoria 5 pueden existir),
        // por eso cada ruta con {id} recibe el id de B de su propia tabla
        List<String> codigos = new ArrayList<>();
        codigos.add(estudianteB);
        codigos.add(alertaB);
        codigos.add(personalB);
        codigos.add(qrB);
        codigos.add(b.slug());

        Map<String, Long> idsPorRuta = new HashMap<>();
        idsPorRuta.put("/api/v1/categorias/", categoriaB);
        idsPorRuta.put("/api/v1/grupos/", grupoB);
        idsPorRuta.put("/api/v1/grados/", gradoB);
        idsPorRuta.put("/api/v1/anios-lectivos/", anioB);
        idsPorRuta.put("/api/v1/notificaciones/", notificacionB);

        Map<String, String> tokensA = new HashMap<>();
        tokensA.put("admin", a.admin());
        tokensA.put("psicorientador", psicorientadorA);
        tokensA.put("docente", docenteA);

        String antes = fotoDe(b);
        List<String> fallas = new ArrayList<>();
        int llamadas = 0;

        for (Ruta ruta : rutas()) {
            if (!ruta.patron().contains("{") || !ruta.patron().startsWith("/api/v1/")) {
                continue;
            }
            if (ruta.patron().startsWith("/api/v1/superadmin/") || ruta.patron().startsWith("/api/v1/public/")) {
                continue;
            }
            List<String> valores = codigos;
            if (ruta.patron().contains("{id}")) {
                valores = new ArrayList<>();
                for (Map.Entry<String, Long> id : idsPorRuta.entrySet()) {
                    if (ruta.patron().startsWith(id.getKey())) {
                        valores.add(String.valueOf(id.getValue()));
                    }
                }
                assertThat(valores).as("falta el id de B para " + ruta.patron()).isNotEmpty();
            }
            for (String valor : valores) {
                for (Map.Entry<String, String> token : tokensA.entrySet()) {
                    String url = armar(ruta.patron(), valor);
                    int codigo = llamar(ruta, url, token.getValue()).getResponse().getStatus();
                    llamadas++;
                    if (codigo < 400 || codigo >= 500) {
                        fallas.add(token.getKey() + " de A: " + ruta.metodo() + " " + url + " -> " + codigo);
                    }
                }
            }
        }

        assertThat(llamadas).isGreaterThan(100);
        assertThat(fallas).isEmpty();
        // los datos de B siguen igual
        assertThat(fotoDe(b)).isEqualTo(antes);

        // y en los listados de A no aparece nada de B
        assertThat(obtener("/api/v1/estudiantes", a.admin()).get("totalElementos").asInt()).isZero();
        assertThat(obtener("/api/v1/alertas", a.admin()).get("totalElementos").asInt()).isZero();
    }

    // ---- apoyo ----

    private JsonNode leer(MvcResult resultado) throws Exception {
        return mapper.readTree(resultado.getResponse().getContentAsString());
    }

    private JsonNode obtener(String ruta, String token) throws Exception {
        return leer(mvc.perform(get(ruta).header("Authorization", token)).andExpect(status().isOk()).andReturn());
    }

    private JsonNode enviar(String ruta, String token, Object cuerpo) throws Exception {
        return leer(mvc.perform(post(ruta).header("Authorization", token)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(cuerpo)))
                .andExpect(status().isCreated()).andReturn());
    }

    // lo que tiene B en la bd, para comparar antes y despues
    private String fotoDe(Colegio colegio) {
        return OWNER.queryForObject("""
                SELECT (SELECT count(*) FROM estudiantes WHERE est_ins_id = ?) || '|' ||
                       (SELECT string_agg(est_nombres || est_apellidos || est_actualizado_en, ',') FROM estudiantes WHERE est_ins_id = ?) || '|' ||
                       (SELECT string_agg(ale_nivel || ale_estado || coalesce(ale_psi_id, 0), ',') FROM alertas WHERE ale_ins_id = ?) || '|' ||
                       (SELECT string_agg(grp_nombre || grp_gra_id, ',') FROM grupos WHERE grp_ins_id = ?) || '|' ||
                       (SELECT string_agg(usu_usuario || usu_activo || usu_contrasena_hash, ',') FROM usuarios WHERE usu_ins_id = ?)
                """, String.class, colegio.id(), colegio.id(), colegio.id(), colegio.id(), colegio.id());
    }

    private Long anioActivo(Colegio colegio) throws Exception {

        for (JsonNode fila : obtener("/api/v1/anios-lectivos", colegio.admin())) {
            if (fila.get("anio").asInt() == ESTE_ANIO) {
                return fila.get("id").asLong();
            }
        }
        throw new IllegalStateException("Sin anio activo");
    }

    private Long primerGrado(Colegio colegio) throws Exception {

        for (JsonNode fila : obtener("/api/v1/grados", colegio.admin())) {
            if (fila.get("nombre").asText().equals("Sexto")) {
                return fila.get("id").asLong();
            }
        }
        throw new IllegalStateException("Sin grado Sexto");
    }

    private Long crearGrupo(Colegio colegio, Long anio, Long grado) throws Exception {

        Map<String, Object> datos = new HashMap<>();
        datos.put("anioId", anio);
        datos.put("gradoId", grado);
        datos.put("nombre", "A");
        return enviar("/api/v1/grupos", colegio.admin(), datos).get("id").asLong();
    }

    private String crearEstudiante(Colegio colegio, String documento, Long grupo) throws Exception {

        Map<String, Object> datos = new HashMap<>();
        datos.put("tipoDoc", "TI");
        datos.put("nroDoc", documento);
        datos.put("nombres", "Luisa");
        datos.put("apellidos", "Prueba");
        datos.put("grupoId", grupo);
        return enviar("/api/v1/estudiantes", colegio.admin(), datos).get("codigo").asText();
    }

    private String crearAlerta(Colegio colegio, String docente, String estudiante) throws Exception {

        Long categoria = obtener("/api/v1/categorias", colegio.admin()).get(0).get("id").asLong();
        Map<String, Object> datos = new HashMap<>();
        datos.put("estudianteCodigo", estudiante);
        datos.put("categoriaId", categoria);
        datos.put("nivel", "ALTO");
        datos.put("descripcion", "Llego con un golpe en el brazo y no quiso decir que paso");
        datos.put("fechaHecho", LocalDate.now().toString());
        return enviar("/api/v1/alertas", docente, datos).get("codigo").asText();
    }

    // devuelve el token de la persona creada
    private String crearPersonal(Colegio colegio, String documento, Rol rol) throws Exception {

        Map<String, Object> datos = new HashMap<>();
        datos.put("tipoDoc", "CC");
        datos.put("nroDoc", documento);
        datos.put("nombres", "Persona");
        datos.put("apellidos", documento);
        datos.put("rol", rol.name());
        enviar("/api/v1/personal", colegio.admin(), datos);

        Long usuario = idUsuario(colegio.id(), documento);
        return token(usuario, colegio.id(), colegio.slug(), rol);
    }
}
