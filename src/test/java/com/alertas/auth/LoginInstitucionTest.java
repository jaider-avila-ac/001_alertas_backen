package com.alertas.auth;

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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

class LoginInstitucionTest extends IntegracionTest {

    static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();


    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @BeforeAll
    static void datos() {

        Long colegioA = crearInstitucion("login-a");
        Long colegioB = crearInstitucion("login-b");
        crearInstitucion("login-inactivo", false, true);

        // todos con contrasena = documento, como los crea el sistema
        crearUsuario(colegioA, "1000", Rol.ADMIN, ENCODER.encode("1000"), false);
        crearUsuario(colegioA, "2000", Rol.DOCENTE, ENCODER.encode("2000"), true);
        crearUsuario(colegioA, "3000", Rol.PSICORIENTADOR, ENCODER.encode("clave-lista-1"), false);
        crearUsuario(colegioA, "4001", Rol.ESTUDIANTE, ENCODER.encode("clave-lista-1"), false);
        crearUsuario(colegioA, "4002", Rol.ESTUDIANTE, ENCODER.encode("clave-lista-1"), false);
        crearUsuario(colegioB, "9000", Rol.DOCENTE, ENCODER.encode("9000"), true);
    }

    private ResultActions login(String slug, String usuario, String contrasena) throws Exception {
        return loginDesde("10.0.0." + (int) (Math.random() * 250), slug, usuario, contrasena);
    }

    private ResultActions loginDesde(String ip, String slug, String usuario, String contrasena) throws Exception {

        String body = "{\"usuario\":\"" + usuario + "\",\"contrasena\":\"" + contrasena + "\"}";
        return mvc.perform(post("/api/v1/public/" + slug + "/auth/login")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String tokenDe(ResultActions resultado) throws Exception {

        MvcResult r = resultado.andReturn();
        JsonNode json = mapper.readTree(r.getResponse().getContentAsString());
        return "Bearer " + json.get("token").asText();
    }

    @Test
    void laInstitucionPublicaMuestraNombreYLaInactivaNo() throws Exception {

        mvc.perform(get("/api/v1/public/login-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Colegio login-a"));

        mvc.perform(get("/api/v1/public/login-inactivo"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("La institucion no esta disponible"));
    }

    @Test
    void soloSeEntraPorElEnlaceDeSuInstitucion() throws Exception {

        login("login-a", "3000", "clave-lista-1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perfil.rol").value("PSICORIENTADOR"))
                .andExpect(jsonPath("$.perfil.institucion.slug").value("login-a"));

        // el docente de B no existe en A
        login("login-a", "9000", "9000")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Usuario o contrasena incorrectos"));

        login("login-inactivo", "3000", "clave-lista-1").andExpect(status().isNotFound());
    }

    @Test
    void primerIngresoSugiereCambiarLaContrasenaSinBloquear() throws Exception {

        String token = tokenDe(login("login-b", "9000", "9000")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perfil.debeCambiarContrasena").value(true)));

        // con la contrasena asignada puede usar todo; el perfil dice que conviene cambiarla
        mvc.perform(get("/api/v1/auth/yo").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.debeCambiarContrasena").value(true));
        mvc.perform(get("/api/v1/prueba/tenant").header("Authorization", token)).andExpect(status().isOk());

        // no puede quedar igual al documento
        mvc.perform(put("/api/v1/auth/contrasena").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"actual\":\"9000\",\"nueva\":\"9000\"}"))
                .andExpect(status().isBadRequest());

        String nuevo = tokenDe(mvc.perform(put("/api/v1/auth/contrasena").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"actual\":\"9000\",\"nueva\":\"mi-clave-nueva\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perfil.debeCambiarContrasena").value(false)));

        mvc.perform(get("/api/v1/prueba/tenant").header("Authorization", nuevo)).andExpect(status().isOk());
        // el token viejo quedo cerrado
        mvc.perform(get("/api/v1/auth/yo").header("Authorization", token)).andExpect(status().isUnauthorized());

        login("login-b", "9000", "9000").andExpect(status().isUnauthorized());
        login("login-b", "9000", "mi-clave-nueva").andExpect(status().isOk());
    }

    @Test
    void elAdministradorNoPuedeCambiarSuContrasena() throws Exception {

        String token = tokenDe(login("login-a", "1000", "1000")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perfil.debeCambiarContrasena").value(false)));

        mvc.perform(put("/api/v1/auth/contrasena").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"actual\":\"1000\",\"nueva\":\"otra-clave-1\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unAdminMarcadoParaCambiarNoQuedaAtrapado() throws Exception {

        Long colegio = crearInstitucion("login-admin-viejo");
        crearUsuario(colegio, "123", Rol.ADMIN, ENCODER.encode("123"), true);

        String token = tokenDe(login("login-admin-viejo", "123", "123")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perfil.debeCambiarContrasena").value(false)));

        mvc.perform(get("/api/v1/institucion").header("Authorization", token)).andExpect(status().isOk());
    }

    @Test
    void despuesDeCincoFallosSeBloquea() throws Exception {

        for (int i = 0; i < 5; i++) {
            login("login-a", "4002", "mala").andExpect(status().isUnauthorized());
        }

        // ni con la clave buena mientras dure el bloqueo
        login("login-a", "4002", "clave-lista-1")
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void variosEstudiantesEquivocadosDesdeLaRedDelColegioNoBloqueanATodos() throws Exception {

        Long colegio = crearInstitucion("login-red");
        crearUsuario(colegio, "31", Rol.ESTUDIANTE, ENCODER.encode("clave-lista-1"), false);

        // 10 estudiantes distintos se equivocan una vez, todos desde la misma ip publica
        for (int i = 0; i < 10; i++) {
            loginDesde("200.1.1.1", "login-red", "otro-" + i, "mala").andExpect(status().isUnauthorized());
        }

        loginDesde("200.1.1.1", "login-red", "31", "clave-lista-1").andExpect(status().isOk());
    }

    @Test
    void usuarioInactivoNoEntra() throws Exception {

        Long colegio = crearInstitucion("login-inactivos");
        Long usuario = crearUsuario(colegio, "55", Rol.DOCENTE, ENCODER.encode("clave-lista-1"), false);
        OWNER.update("UPDATE usuarios SET usu_activo = false WHERE usu_id = ?", usuario);

        login("login-inactivos", "55", "clave-lista-1")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Tu usuario esta inactivo, comunicate con el administrador de tu institucion"));
    }

    @Test
    void cerrarElAccesoDeEstudiantes() throws Exception {

        Long colegio = crearInstitucion("login-vacaciones");
        Long admin = crearUsuario(colegio, "1", Rol.ADMIN, "x", false);
        crearUsuario(colegio, "77", Rol.ESTUDIANTE, ENCODER.encode("clave-lista-1"), false);

        String tokenAdmin = token(admin, colegio, "login-vacaciones", Rol.ADMIN);
        String tokenEst = tokenDe(login("login-vacaciones", "77", "clave-lista-1").andExpect(status().isOk()));

        mvc.perform(patch("/api/v1/institucion/acceso-estudiantes").header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accesoEstudiantes").value(false));

        // aplica de inmediato para la sesion abierta y para entrar de nuevo
        mvc.perform(get("/api/v1/auth/yo").header("Authorization", tokenEst)).andExpect(status().isForbidden());
        login("login-vacaciones", "77", "clave-lista-1")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("El acceso de estudiantes esta deshabilitado en este momento"));

        // solo el admin maneja el interruptor
        mvc.perform(patch("/api/v1/institucion/acceso-estudiantes").header("Authorization", tokenEst)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void elSuperadminLeAsignaContrasenaAlAdmin() throws Exception {

        MvcResult r = mvc.perform(post("/api/v1/superadmin/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"" + SUPERADMIN_USUARIO + "\",\"contrasena\":\"" + SUPERADMIN_CONTRASENA + "\"}"))
                .andReturn();
        String sa = "Bearer " + mapper.readTree(r.getResponse().getContentAsString()).get("token").asText();

        Long colegio = crearInstitucion("login-asignar");
        Long admin = crearUsuario(colegio, "880", Rol.ADMIN, ENCODER.encode("880"), false);
        OWNER.update("""
                INSERT INTO personal (per_ins_id, per_usu_id, per_codigo, per_tipo_doc, per_nro_doc, per_nombres, per_apellidos)
                VALUES (?, ?, 'codigoAdmin1', 'CC', '880', 'Admin', 'Uno')""", colegio, admin);

        mvc.perform(put("/api/v1/superadmin/instituciones/login-asignar/administradores/codigoAdmin1/contrasena")
                        .header("Authorization", sa)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nueva\":\"clave-del-admin\"}"))
                .andExpect(status().isOk());

        login("login-asignar", "880", "880").andExpect(status().isUnauthorized());
        login("login-asignar", "880", "clave-del-admin")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perfil.nombres").value("Admin"))
                .andExpect(jsonPath("$.perfil.debeCambiarContrasena").value(false));
    }
}
