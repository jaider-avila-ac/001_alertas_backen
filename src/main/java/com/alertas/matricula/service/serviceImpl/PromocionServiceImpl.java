package com.alertas.matricula.service.serviceImpl;

import com.alertas.bitacora.service.BitacoraService;
import com.alertas.estructura.dto.AnioLectivoResponse;
import com.alertas.estructura.dto.GradoResponse;
import com.alertas.estructura.dto.GrupoResponse;
import com.alertas.estructura.service.EstructuraService;
import com.alertas.matricula.dto.AsignacionGrupo;
import com.alertas.matricula.dto.ConfirmarPromocionRequest;
import com.alertas.matricula.dto.GrupoPorCrear;
import com.alertas.matricula.dto.GrupoPromocion;
import com.alertas.matricula.dto.PromocionResponse;
import com.alertas.matricula.dto.ResultadoPromocionResponse;
import com.alertas.matricula.model.Matricula;
import com.alertas.matricula.repository.MatriculaRepository;
import com.alertas.matricula.service.PromocionService;
import com.alertas.shared.TenantSupport;
import com.alertas.shared.exception.ApiException;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PromocionServiceImpl implements PromocionService {

    private final MatriculaRepository repository;
    private final EstructuraService estructuraService;
    private final BitacoraService bitacoraService;
    private final EntityManager em;

    public PromocionServiceImpl(
            MatriculaRepository repository,
            EstructuraService estructuraService,
            BitacoraService bitacoraService,
            EntityManager em) {

        this.repository = repository;
        this.estructuraService = estructuraService;
        this.bitacoraService = bitacoraService;
        this.em = em;
    }

    // ---------------------------------------------------------------- vista previa

    @Override
    @Transactional(readOnly = true)
    public PromocionResponse vistaPrevia() {

        TenantSupport.requireTenant(em);
        return armar(new Contexto());
    }

    private PromocionResponse armar(Contexto ctx) {

        // [grupo, por promover, ya promovidos, graduados]
        Map<Long, long[]> resumen = new HashMap<>();

        for (Object[] fila : repository.resumenPromocion(ctx.origen.id(), ctx.destino.id())) {
            long[] cifras = {
                    ((Number) fila[1]).longValue(),
                    ((Number) fila[2]).longValue(),
                    ((Number) fila[3]).longValue()
            };
            resumen.put(((Number) fila[0]).longValue(), cifras);
        }

        List<GrupoPromocion> grupos = new ArrayList<>();

        for (GradoResponse grado : ctx.grados) {
            GradoResponse siguiente = ctx.siguiente(grado);

            for (GrupoResponse grupo : ctx.gruposOrigen(grado.id())) {
                long[] cifras = resumen.getOrDefault(grupo.id(), new long[] {0, 0, 0});

                Long sugerido = null;
                if (siguiente != null) {
                    sugerido = ctx.destinoSugerido(grupo, siguiente);
                }

                grupos.add(new GrupoPromocion(
                        grupo.id(),
                        grupo.gradoNombre(),
                        grupo.nombre(),
                        cifras[0],
                        cifras[1],
                        cifras[2],
                        siguiente == null,
                        sugerido));
            }
        }

        return new PromocionResponse(
                ctx.origen.id(),
                ctx.origen.anio(),
                ctx.destino.id(),
                ctx.destino.anio(),
                gruposPorCrear(ctx),
                grupos,
                ctx.gruposDestino);
    }

    // por cada grado del anio siguiente: los mismos nombres de grupo que tenia el grado anterior
    // (6A y 6B pasan a 7A y 7B). el grado mas bajo copia sus propios grupos para los que entran
    private List<GrupoPorCrear> gruposPorCrear(Contexto ctx) {

        List<GrupoPorCrear> faltan = new ArrayList<>();

        for (GradoResponse grado : ctx.grados) {
            GradoResponse anterior = ctx.anterior(grado);

            List<GrupoResponse> modelo = new ArrayList<>();
            if (anterior != null) {
                modelo = ctx.gruposOrigen(anterior.id());
            }
            if (modelo.isEmpty()) {
                modelo = ctx.gruposOrigen(grado.id());
            }

            List<GrupoResponse> existentes = ctx.gruposDestinoDelGrado(grado.id());

            for (GrupoResponse grupo : modelo) {
                boolean existe = false;
                for (GrupoResponse destino : existentes) {
                    if (destino.nombre().equalsIgnoreCase(grupo.nombre())) {
                        existe = true;
                    }
                }

                // si ya tiene tantos grupos como el modelo no se crean mas, aunque se llamen distinto
                if (!existe && existentes.size() + contar(faltan, grado.id()) < modelo.size()) {
                    faltan.add(new GrupoPorCrear(grado.id(), grado.nombre(), grupo.nombre()));
                }
            }
        }

        return faltan;
    }

    private int contar(List<GrupoPorCrear> lista, Long gradoId) {

        int total = 0;
        for (GrupoPorCrear grupo : lista) {
            if (grupo.gradoId().equals(gradoId)) {
                total++;
            }
        }
        return total;
    }

    // ---------------------------------------------------------------- preparar grupos

    @Override
    @Transactional
    public PromocionResponse prepararGrupos() {

        TenantSupport.requireTenant(em);

        Contexto ctx = new Contexto();
        List<GrupoPorCrear> faltan = gruposPorCrear(ctx);

        for (GrupoPorCrear grupo : faltan) {
            estructuraService.crearGrupo(ctx.destino.id(), grupo.gradoId(), grupo.nombre());
        }

        // se vuelve a leer con los grupos nuevos
        return armar(new Contexto());
    }

    // ---------------------------------------------------------------- confirmar

    @Override
    @Transactional
    public ResultadoPromocionResponse confirmar(ConfirmarPromocionRequest request) {

        TenantSupport.requireTenant(em);

        Contexto ctx = new Contexto();

        // lo que mande el admin; lo que no mande va al destino sugerido
        Map<Long, Long> asignado = new HashMap<>();
        Map<Long, Boolean> enviado = new HashMap<>();

        if (request != null && request.asignaciones() != null) {
            for (AsignacionGrupo asignacion : request.asignaciones()) {
                asignado.put(asignacion.grupoOrigenId(), asignacion.grupoDestinoId());
                enviado.put(asignacion.grupoOrigenId(), true);
            }
        }

        int promovidos = 0;
        int graduados = 0;
        int sinDestino = 0;

        for (GradoResponse grado : ctx.grados) {
            GradoResponse siguiente = ctx.siguiente(grado);

            for (GrupoResponse grupo : ctx.gruposOrigen(grado.id())) {
                List<Matricula> porPromover = repository.porPromover(grupo.id(), ctx.destino.id());

                if (porPromover.isEmpty()) {
                    continue;
                }

                // ultimo grado: se graduan
                if (siguiente == null) {
                    for (Matricula matricula : porPromover) {
                        matricula.cerrar(Matricula.GRADUADA, null);
                        graduados++;
                    }
                    continue;
                }

                Long destinoId;
                if (enviado.containsKey(grupo.id())) {
                    destinoId = asignado.get(grupo.id());
                } else {
                    destinoId = ctx.destinoSugerido(grupo, siguiente);
                }

                if (destinoId == null) {
                    sinDestino = sinDestino + porPromover.size();
                    continue;
                }

                GrupoResponse destino = ctx.grupoDestino(destinoId);

                for (Matricula anterior : porPromover) {
                    Matricula nueva = new Matricula();
                    nueva.setInstitucionId(anterior.getInstitucionId());
                    nueva.setEstudianteId(anterior.getEstudianteId());
                    nueva.setAnioId(destino.anioId());
                    nueva.setGrupoId(destino.id());

                    // si el admin lo mando al mismo grado, repite
                    if (destino.gradoOrden() == grupo.gradoOrden()) {
                        nueva.setOrigen(Matricula.REPITE);
                        anterior.cerrar(Matricula.REPROBADA, null);
                    } else {
                        nueva.setOrigen(Matricula.PROMOCION);
                        anterior.cerrar(Matricula.PROMOVIDA, null);
                    }

                    repository.save(nueva);
                    promovidos++;
                }
            }
        }

        bitacoraService.registrar("PROMOCION", "anio_lectivo", ctx.destino.id(),
                ctx.origen.anio() + " a " + ctx.destino.anio() + ": " + promovidos + " promovidos, "
                        + graduados + " graduados, " + sinDestino + " sin grupo destino");

        return new ResultadoPromocionResponse(promovidos, graduados, sinDestino);
    }

    // ---------------------------------------------------------------- datos de trabajo

    // anios, grados y grupos que se usan en las tres operaciones
    private class Contexto {

        final AnioLectivoResponse origen;
        final AnioLectivoResponse destino;
        final List<GradoResponse> grados = new ArrayList<>();
        final List<GrupoResponse> gruposOrigen;
        final List<GrupoResponse> gruposDestino;

        Contexto() {

            origen = estructuraService.anioActivo();

            if (origen == null) {
                throw ApiException.conflicto("No hay un anio activo");
            }

            // el anio siguiente mas cercano que ya este creado
            AnioLectivoResponse siguiente = null;
            for (AnioLectivoResponse anio : estructuraService.listarAnios()) {
                if (anio.anio() > origen.anio() && (siguiente == null || anio.anio() < siguiente.anio())) {
                    siguiente = anio;
                }
            }

            if (siguiente == null) {
                throw ApiException.conflicto("Crea primero el anio " + (origen.anio() + 1) + " en Grados y grupos");
            }

            destino = siguiente;

            // solo los grados que ofrece la institucion, de menor a mayor
            for (GradoResponse grado : estructuraService.listarGrados()) {
                if (grado.activo()) {
                    grados.add(grado);
                }
            }
            grados.sort((a, b) -> Integer.compare(a.orden(), b.orden()));

            gruposOrigen = ordenar(estructuraService.listarGrupos(origen.id()));
            gruposDestino = ordenar(estructuraService.listarGrupos(destino.id()));
        }

        GradoResponse siguiente(GradoResponse grado) {

            for (GradoResponse otro : grados) {
                if (otro.orden() > grado.orden()) {
                    return otro;
                }
            }
            return null;
        }

        GradoResponse anterior(GradoResponse grado) {

            GradoResponse anterior = null;
            for (GradoResponse otro : grados) {
                if (otro.orden() < grado.orden()) {
                    anterior = otro;
                }
            }
            return anterior;
        }

        List<GrupoResponse> gruposOrigen(Long gradoId) {
            return delGrado(gruposOrigen, gradoId);
        }

        List<GrupoResponse> gruposDestinoDelGrado(Long gradoId) {
            return delGrado(gruposDestino, gradoId);
        }

        // el primer grupo va al primero del grado siguiente, el segundo al segundo...
        // asi funciona aunque los grupos nuevos tengan otro nombre
        Long destinoSugerido(GrupoResponse grupo, GradoResponse siguiente) {

            List<GrupoResponse> origenes = gruposOrigen(grupo.gradoId());
            List<GrupoResponse> destinos = gruposDestinoDelGrado(siguiente.id());

            // si hay uno con el mismo nombre, ese
            for (GrupoResponse destino : destinos) {
                if (destino.nombre().equalsIgnoreCase(grupo.nombre())) {
                    return destino.id();
                }
            }

            int posicion = origenes.indexOf(grupo);
            if (posicion >= 0 && posicion < destinos.size()) {
                return destinos.get(posicion).id();
            }

            return null;
        }

        GrupoResponse grupoDestino(Long grupoId) {

            for (GrupoResponse grupo : gruposDestino) {
                if (grupo.id().equals(grupoId)) {
                    return grupo;
                }
            }
            throw ApiException.invalido("El grupo destino no es del anio " + destino.anio());
        }

        private List<GrupoResponse> delGrado(List<GrupoResponse> grupos, Long gradoId) {

            List<GrupoResponse> suyos = new ArrayList<>();
            for (GrupoResponse grupo : grupos) {
                if (grupo.gradoId().equals(gradoId)) {
                    suyos.add(grupo);
                }
            }
            return suyos;
        }

        private List<GrupoResponse> ordenar(List<GrupoResponse> grupos) {

            List<GrupoResponse> copia = new ArrayList<>(grupos);
            copia.sort((a, b) -> {
                if (a.gradoOrden() != b.gradoOrden()) {
                    return Integer.compare(a.gradoOrden(), b.gradoOrden());
                }
                return a.nombre().compareToIgnoreCase(b.nombre());
            });
            return copia;
        }
    }
}
