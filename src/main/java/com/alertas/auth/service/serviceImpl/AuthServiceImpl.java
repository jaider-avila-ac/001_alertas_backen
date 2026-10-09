package com.alertas.auth.service.serviceImpl;

import com.alertas.auth.dto.CambiarContrasenaRequest;
import com.alertas.auth.dto.LoginRequest;
import com.alertas.auth.dto.LoginResponse;
import com.alertas.auth.dto.PerfilResponse;
import com.alertas.auth.model.Rol;
import com.alertas.auth.model.UsuarioAutenticado;
import com.alertas.auth.service.AuthService;
import com.alertas.auth.service.JwtService;
import com.alertas.auth.service.LimiteIntentosService;
import com.alertas.auth.service.SesionService;
import com.alertas.estudiante.service.EstudianteService;
import com.alertas.institucion.dto.EstadoInstitucion;
import com.alertas.institucion.dto.InstitucionPublicaResponse;
import com.alertas.institucion.service.InstitucionService;
import com.alertas.shared.dto.NombrePersona;
import com.alertas.personal.service.PersonalService;
import com.alertas.shared.TenantSupport;
import com.alertas.shared.exception.ApiException;
import com.alertas.usuario.model.Usuario;
import com.alertas.usuario.service.UsuarioService;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {

    private static final String CREDENCIALES_MALAS = "Usuario o contrasena incorrectos";

    private final UsuarioService usuarioService;
    private final PersonalService personalService;
    private final EstudianteService estudianteService;
    private final InstitucionService institucionService;
    private final JwtService jwtService;
    private final LimiteIntentosService limiteIntentos;
    private final SesionService sesionService;
    private final PasswordEncoder passwordEncoder;
    private final EntityManager em;

    public AuthServiceImpl(
            UsuarioService usuarioService,
            PersonalService personalService,
            EstudianteService estudianteService,
            InstitucionService institucionService,
            JwtService jwtService,
            LimiteIntentosService limiteIntentos,
            SesionService sesionService,
            PasswordEncoder passwordEncoder,
            EntityManager em) {

        this.usuarioService = usuarioService;
        this.personalService = personalService;
        this.estudianteService = estudianteService;
        this.institucionService = institucionService;
        this.jwtService = jwtService;
        this.limiteIntentos = limiteIntentos;
        this.sesionService = sesionService;
        this.passwordEncoder = passwordEncoder;
        this.em = em;
    }

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request, String ip, String userAgent) {

        Long institucionId = TenantSupport.requireTenant(em);
        String documento = request.usuario().trim();

        // el mismo documento puede existir en otro colegio, por eso la clave lleva la institucion
        String claveUsuario = "u:" + institucionId + ":" + documento;
        String claveIp = "ip:" + ip;

        if (limiteIntentos.estaBloqueado(claveUsuario, claveIp)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Demasiados intentos fallidos. Espera " + limiteIntentos.getBloqueoMinutos() + " minutos");
        }

        Usuario usuario = usuarioService.buscarPorDocumento(documento);

        if (usuario == null || !passwordEncoder.matches(request.contrasena(), usuario.getContrasenaHash())) {
            limiteIntentos.registrarFallo(claveUsuario, claveIp);
            throw ApiException.noAutorizado(CREDENCIALES_MALAS);
        }

        // estos mensajes solo los ve quien ya puso la contrasena correcta
        if (!usuario.isActivo()) {
            throw ApiException.prohibido("Tu usuario esta inactivo, comunicate con el administrador de tu institucion");
        }

        EstadoInstitucion estado = institucionService.estadoPorId(institucionId);

        if (usuario.getRol() == Rol.ESTUDIANTE && !estado.accesoEstudiantes()) {
            throw ApiException.prohibido("El acceso de estudiantes esta deshabilitado en este momento");
        }

        limiteIntentos.limpiar(claveUsuario);
        usuarioService.registrarIngreso(usuario.getId());

        // el admin no puede cambiar su contrasena: si quedo marcado (datos viejos) no se le exige,
        // porque se quedaria atrapado en la pantalla de cambio
        boolean debeCambiar = usuario.isDebeCambiarContrasena() && usuario.getRol() != Rol.ADMIN;

        NombrePersona nombre = buscarNombre(usuario);
        String nombres = usuario.getUsuario();
        String apellidos = "";
        if (nombre != null) {
            nombres = nombre.nombres();
            apellidos = nombre.apellidos();
        }

        String sesionId = sesionService.abrir(
                institucionId, usuario.getId(), usuario.getRol(), userAgent, usuario.getUsuario(), nombres, apellidos);
        String token = jwtService.generar(
                usuario.getId(), institucionId, estado.slug(), usuario.getRol(), debeCambiar, sesionId);

        return new LoginResponse(token, armarPerfil(usuario));
    }

    @Override
    public void salir() {

        UsuarioAutenticado actual = UsuarioAutenticado.actual();

        if (actual.institucionId() != null && actual.sesionId() != null) {
            sesionService.cerrar(actual.institucionId(), actual.sesionId());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PerfilResponse perfil() {

        TenantSupport.requireTenant(em);

        Usuario usuario = usuarioService.buscarPorId(UsuarioAutenticado.actual().id());
        return armarPerfil(usuario);
    }

    @Override
    @Transactional
    public LoginResponse cambiarContrasena(CambiarContrasenaRequest request) {

        Long institucionId = TenantSupport.requireTenant(em);
        UsuarioAutenticado actual = UsuarioAutenticado.actual();

        usuarioService.cambiarContrasenaPropia(actual.id(), request.actual(), request.nueva());

        Usuario usuario = usuarioService.buscarPorId(actual.id());
        // sigue en la misma sesion; las de otros equipos ya se cerraron
        String token = jwtService.generar(
                usuario.getId(), institucionId, actual.slug(), usuario.getRol(), false, actual.sesionId());

        return new LoginResponse(token, armarPerfil(usuario));
    }

    // del personal o del estudiante, null si no tiene (no deberia pasar)
    private NombrePersona buscarNombre(Usuario usuario) {

        NombrePersona nombre = personalService.buscarNombre(usuario.getId());

        if (nombre == null) {
            nombre = estudianteService.buscarNombre(usuario.getId());
        }

        return nombre;
    }

    private PerfilResponse armarPerfil(Usuario usuario) {

        String nombres = usuario.getUsuario();
        String apellidos = "";

        NombrePersona nombre = buscarNombre(usuario);

        if (nombre != null) {
            nombres = nombre.nombres();
            apellidos = nombre.apellidos();
        }

        InstitucionPublicaResponse institucion = institucionService.buscarPublica(usuario.getInstitucionId());

        boolean debeCambiar = usuario.isDebeCambiarContrasena() && usuario.getRol() != Rol.ADMIN;

        return new PerfilResponse(
                usuario.getUsuario(),
                nombres,
                apellidos,
                usuario.getRol(),
                debeCambiar,
                institucion,
                institucionService.valoracionesActivas(usuario.getInstitucionId()));
    }
}
