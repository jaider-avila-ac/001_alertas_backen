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
import com.alertas.institucion.dto.EstadoInstitucion;
import com.alertas.institucion.dto.InstitucionPublicaResponse;
import com.alertas.institucion.service.InstitucionService;
import com.alertas.personal.dto.NombrePersona;
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
    private final InstitucionService institucionService;
    private final JwtService jwtService;
    private final LimiteIntentosService limiteIntentos;
    private final PasswordEncoder passwordEncoder;
    private final EntityManager em;

    public AuthServiceImpl(
            UsuarioService usuarioService,
            PersonalService personalService,
            InstitucionService institucionService,
            JwtService jwtService,
            LimiteIntentosService limiteIntentos,
            PasswordEncoder passwordEncoder,
            EntityManager em) {

        this.usuarioService = usuarioService;
        this.personalService = personalService;
        this.institucionService = institucionService;
        this.jwtService = jwtService;
        this.limiteIntentos = limiteIntentos;
        this.passwordEncoder = passwordEncoder;
        this.em = em;
    }

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request, String ip) {

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

        String token = jwtService.generar(usuario.getId(), institucionId, estado.slug(), usuario.getRol(), debeCambiar);

        return new LoginResponse(token, armarPerfil(usuario));
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
        String token = jwtService.generar(usuario.getId(), institucionId, actual.slug(), usuario.getRol(), false);

        return new LoginResponse(token, armarPerfil(usuario));
    }

    private PerfilResponse armarPerfil(Usuario usuario) {

        String nombres = usuario.getUsuario();
        String apellidos = "";

        NombrePersona nombre = personalService.buscarNombre(usuario.getId());

        if (nombre != null) {
            nombres = nombre.nombres();
            apellidos = nombre.apellidos();
        }

        InstitucionPublicaResponse institucion = institucionService.buscarPublica(usuario.getInstitucionId());

        boolean debeCambiar = usuario.isDebeCambiarContrasena() && usuario.getRol() != Rol.ADMIN;

        return new PerfilResponse(
                usuario.getId(),
                usuario.getUsuario(),
                nombres,
                apellidos,
                usuario.getRol(),
                debeCambiar,
                institucion);
    }
}
