package com.alertas.auth.service.serviceImpl;

import com.alertas.auth.model.Navegador;
import com.alertas.auth.model.Rol;
import com.alertas.auth.model.SesionAbierta;
import com.alertas.auth.model.UsuarioAutenticado;
import com.alertas.auth.service.JwtService;
import com.alertas.auth.service.SesionService;
import com.alertas.notificacion.service.EnVivoService;
import com.alertas.shared.CodigoAleatorio;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

// en redis, todo vence cuando venceria el token:
//   sesion:datos:{codigo}   "institucion|usuario|rol|inicio|dispositivo|sistema|navegador|documento|nombres|apellidos"
//   sesion:vivo:{codigo}    ultima actividad (ms). si no existe, la sesion esta cerrada
//   sesiones:ins:{id}       codigos de la institucion, ordenados por inicio
//   sesiones:usu:{ins}:{id} codigos de un usuario
@Service
public class SesionServiceImpl implements SesionService {

    private static final Logger LOG = LoggerFactory.getLogger(SesionServiceImpl.class);

    private static final long TOQUE_MS = 60_000;

    private static final Rol[] ROLES_INSTITUCION = {Rol.ADMIN, Rol.DOCENTE, Rol.PSICORIENTADOR, Rol.ESTUDIANTE};

    private final StringRedisTemplate redis;
    private final JwtService jwtService;
    private final EnVivoService enVivoService;

    public SesionServiceImpl(StringRedisTemplate redis, JwtService jwtService, EnVivoService enVivoService) {

        this.redis = redis;
        this.jwtService = jwtService;
        this.enVivoService = enVivoService;
    }

    // ---------------------------------------------------------------- abrir y actividad

    @Override
    public String abrir(
            Long institucionId, Long usuarioId, Rol rol, String userAgent, String documento, String nombres, String apellidos) {

        String codigo = CodigoAleatorio.generar();
        long ahora = Instant.now().toEpochMilli();
        Duration duracion = jwtService.getDuracion();
        Navegador navegador = Navegador.leer(userAgent);

        String datos = institucionId + "|" + usuarioId + "|" + rol.name() + "|" + ahora + "|"
                + navegador.dispositivo() + "|" + navegador.sistema() + "|" + navegador.navegador() + "|"
                + limpiar(documento) + "|" + limpiar(nombres) + "|" + limpiar(apellidos);

        redis.opsForValue().set(claveDatos(codigo), datos, duracion);
        redis.opsForValue().set(claveVivo(codigo), String.valueOf(ahora), duracion);

        redis.opsForZSet().add(claveInstitucion(institucionId), codigo, ahora);
        redis.expire(claveInstitucion(institucionId), duracion);

        redis.opsForSet().add(claveUsuarioSesiones(institucionId, usuarioId), codigo);
        redis.expire(claveUsuarioSesiones(institucionId, usuarioId), duracion);

        avisarCambio(institucionId, "abierta", codigo, rol.name());
        return codigo;
    }

    @Override
    public void registrarActividad(String sesionId) {

        if (sesionId == null) {
            return;
        }

        // se conserva el vencimiento que ya tenia
        String clave = claveVivo(sesionId);
        Long restante = redis.getExpire(clave, TimeUnit.MILLISECONDS);

        if (restante == null || restante <= 0) {
            return;
        }

        redis.opsForValue().set(clave, String.valueOf(Instant.now().toEpochMilli()), Duration.ofMillis(restante));

        // la ultima actividad cambio: el panel del superadmin la muestra
        String dato = redis.opsForValue().get(claveDatos(sesionId));
        if (dato != null) {
            String[] partes = dato.split("\\|", -1);
            avisarCambio(Long.valueOf(partes[0]), "actividad", sesionId, partes[2]);
        }
    }

    @Override
    public SesionAbierta buscar(Long institucionId, String sesionId) {

        if (sesionId == null) {
            return null;
        }

        String dato = redis.opsForValue().get(claveDatos(sesionId));
        String vivo = redis.opsForValue().get(claveVivo(sesionId));

        if (dato == null || vivo == null) {
            return null;
        }

        SesionAbierta sesion = leer(sesionId, dato, vivo);

        if (sesion == null || !institucionId.equals(sesion.institucionId())) {
            return null;
        }

        return sesion;
    }

    // ---------------------------------------------------------------- listar

    @Override
    public List<SesionAbierta> listar(Long institucionId) {

        String claveIns = claveInstitucion(institucionId);
        long vencidas = Instant.now().minus(jwtService.getDuracion()).toEpochMilli();

        redis.opsForZSet().removeRangeByScore(claveIns, 0, vencidas);
        Set<String> codigos = redis.opsForZSet().reverseRange(claveIns, 0, -1);

        List<SesionAbierta> abiertas = new ArrayList<>();

        if (codigos == null || codigos.isEmpty()) {
            return abiertas;
        }

        List<String> lista = new ArrayList<>(codigos);
        List<String> clavesDatos = new ArrayList<>();
        List<String> clavesVivo = new ArrayList<>();

        for (String codigo : lista) {
            clavesDatos.add(claveDatos(codigo));
            clavesVivo.add(claveVivo(codigo));
        }

        // dos idas a redis para todas las sesiones
        List<String> datos = redis.opsForValue().multiGet(clavesDatos);
        List<String> vivos = redis.opsForValue().multiGet(clavesVivo);

        if (datos == null || vivos == null) {
            return abiertas;
        }

        for (int i = 0; i < lista.size(); i++) {
            String dato = datos.get(i);
            String vivo = vivos.get(i);

            if (dato == null || vivo == null) {
                // ya se cerro o vencio: se quita del indice
                redis.opsForZSet().remove(claveIns, lista.get(i));
                continue;
            }

            SesionAbierta sesion = leer(lista.get(i), dato, vivo);

            if (sesion != null && institucionId.equals(sesion.institucionId())) {
                abiertas.add(sesion);
            }
        }

        return abiertas;
    }

    // ---------------------------------------------------------------- cerrar

    @Override
    public boolean cerrar(Long institucionId, String sesionId) {

        if (sesionId == null) {
            return false;
        }

        String dato = redis.opsForValue().get(claveDatos(sesionId));
        String vivo = redis.opsForValue().get(claveVivo(sesionId));

        if (dato == null || vivo == null) {
            redis.opsForZSet().remove(claveInstitucion(institucionId), sesionId);
            return false;
        }

        SesionAbierta sesion = leer(sesionId, dato, vivo);

        if (sesion == null || !institucionId.equals(sesion.institucionId())) {
            return false;
        }

        quitar(sesion, true);
        return true;
    }

    @Override
    public int cerrarTodas(Long institucionId) {

        // tambien saca a los tokens viejos que no tienen sesion registrada
        for (Rol rol : ROLES_INSTITUCION) {
            marcarCierre(claveRol(institucionId, rol));
        }

        List<SesionAbierta> abiertas = listar(institucionId);

        for (SesionAbierta sesion : abiertas) {
            quitar(sesion, false);
        }

        // un solo aviso al panel, no uno por sesion
        avisarCambio(institucionId, "todas", "-", "-");
        return abiertas.size();
    }

    @Override
    public void cerrarSesiones(Long institucionId, Long usuarioId) {
        cerrarSesiones(institucionId, usuarioId, null);
    }

    @Override
    public void cerrarSesiones(Long institucionId, Long usuarioId, String excepto) {

        marcarCierre(claveUsuario(institucionId, usuarioId));

        // el superadmin no tiene sesiones registradas
        if (institucionId == null) {
            return;
        }

        Set<String> codigos = redis.opsForSet().members(claveUsuarioSesiones(institucionId, usuarioId));

        if (codigos == null) {
            return;
        }

        for (String codigo : codigos) {
            if (!codigo.equals(excepto)) {
                cerrar(institucionId, codigo);
            }
        }
    }

    @Override
    public void cerrarSesionesDelRol(Long institucionId, Rol rol) {

        marcarCierre(claveRol(institucionId, rol));

        for (SesionAbierta sesion : listar(institucionId)) {
            if (sesion.rol() == rol) {
                quitar(sesion, false);
            }
        }

        avisarCambio(institucionId, "rol", "-", rol.name());
    }

    // ---------------------------------------------------------------- cada solicitud

    @Override
    public boolean sigueVigente(UsuarioAutenticado usuario) {

        List<String> claves = new ArrayList<>();
        claves.add(claveUsuario(usuario.institucionId(), usuario.id()));

        if (usuario.institucionId() != null) {
            claves.add(claveRol(usuario.institucionId(), usuario.rol()));
        }

        if (usuario.sesionId() != null) {
            claves.add(claveVivo(usuario.sesionId()));
        }

        // una sola ida a redis para todas las claves
        List<String> valores = redis.opsForValue().multiGet(claves);

        if (valores == null) {
            return true;
        }

        long emitidoEn = usuario.emitidoEn().toEpochMilli();
        int cierres = claves.size();

        if (usuario.sesionId() != null) {
            cierres = cierres - 1;
        }

        for (int i = 0; i < cierres; i++) {
            String valor = valores.get(i);
            if (valor != null && emitidoEn < Long.parseLong(valor)) {
                return false;
            }
        }

        if (usuario.sesionId() == null) {
            return true;
        }

        // la sesion se cerro aunque el token no haya vencido
        String vivo = valores.get(valores.size() - 1);

        if (vivo == null) {
            return false;
        }

        if (Instant.now().toEpochMilli() - Long.parseLong(vivo) > TOQUE_MS) {
            registrarActividad(usuario.sesionId());
        }

        return true;
    }

    // ---------------------------------------------------------------- ayudas

    // avisarPanel: false cuando se cierran muchas a la vez (se avisa una sola vez al final)
    private void quitar(SesionAbierta sesion, boolean avisarPanel) {

        redis.delete(claveVivo(sesion.codigo()));
        redis.delete(claveDatos(sesion.codigo()));
        redis.opsForZSet().remove(claveInstitucion(sesion.institucionId()), sesion.codigo());
        redis.opsForSet().remove(claveUsuarioSesiones(sesion.institucionId(), sesion.usuarioId()), sesion.codigo());

        // el navegador de esa sesion sale al instante
        enVivoService.avisarSesionCerrada(sesion.institucionId(), sesion.usuarioId(), sesion.codigo());
        if (avisarPanel) {
            avisarCambio(sesion.institucionId(), "cerrada", sesion.codigo(), sesion.rol().name());
        }
    }

    // "institucion|tipo|codigo|rol". tipo: abierta, actividad, cerrada, rol (todas las de un rol) o todas
    private void avisarCambio(Long institucionId, String tipo, String codigo, String rol) {

        try {
            redis.convertAndSend(CANAL_CAMBIOS, institucionId + "|" + tipo + "|" + codigo + "|" + rol);
        } catch (RuntimeException e) {
            // sin el aviso el panel se pone al dia en la siguiente consulta
            LOG.warn("No se pudo avisar el cambio de sesiones: {}", e.getMessage());
        }
    }

    private SesionAbierta leer(String codigo, String dato, String vivo) {

        String[] partes = dato.split("\\|", -1);

        if (partes.length < 7) {
            return null;
        }

        // las sesiones guardadas antes de tener el nombre
        String documento = "";
        String nombres = "";
        String apellidos = "";
        if (partes.length >= 10) {
            documento = partes[7];
            nombres = partes[8];
            apellidos = partes[9];
        }

        Rol rol;

        try {
            rol = Rol.valueOf(partes[2]);
        } catch (IllegalArgumentException e) {
            return null;
        }

        return new SesionAbierta(
                codigo,
                Long.valueOf(partes[0]),
                Long.valueOf(partes[1]),
                rol,
                Instant.ofEpochMilli(Long.parseLong(partes[3])),
                Instant.ofEpochMilli(Long.parseLong(vivo)),
                partes[4],
                partes[5],
                partes[6],
                documento,
                nombres,
                apellidos);
    }

    // el separador no puede ir dentro de un dato
    private String limpiar(String texto) {

        if (texto == null) {
            return "";
        }

        return texto.replace('|', ' ');
    }

    private void marcarCierre(String clave) {

        String ahora = String.valueOf(Instant.now().toEpochMilli());

        // la clave dura lo mismo que un token, despues ya no hace falta
        redis.opsForValue().set(clave, ahora, jwtService.getDuracion());
    }

    private String claveUsuario(Long institucionId, Long usuarioId) {

        String ins = "sa";

        if (institucionId != null) {
            ins = institucionId.toString();
        }

        return "sesion:inval:" + ins + ":" + usuarioId;
    }

    private String claveRol(Long institucionId, Rol rol) {
        return "sesion:inval:rol:" + institucionId + ":" + rol.name();
    }

    private String claveDatos(String codigo) {
        return "sesion:datos:" + codigo;
    }

    private String claveVivo(String codigo) {
        return "sesion:vivo:" + codigo;
    }

    private String claveInstitucion(Long institucionId) {
        return "sesiones:ins:" + institucionId;
    }

    private String claveUsuarioSesiones(Long institucionId, Long usuarioId) {
        return "sesiones:usu:" + institucionId + ":" + usuarioId;
    }
}
