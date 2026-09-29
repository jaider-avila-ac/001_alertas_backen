package com.alertas.estadistica.service.serviceImpl;

import com.alertas.estadistica.dto.ComparativoResponse;
import com.alertas.estadistica.dto.ConteoResponse;
import com.alertas.estadistica.dto.EstadisticasGlobalesResponse;
import com.alertas.estadistica.dto.ResumenGlobalResponse;
import com.alertas.estadistica.dto.SmsMesResponse;
import com.alertas.estadistica.repository.EstadisticaGlobalRepository;
import com.alertas.estadistica.service.EstadisticaGlobalService;
import com.alertas.institucion.model.Institucion;
import com.alertas.institucion.repository.InstitucionRepository;
import com.alertas.shared.Fechas;
import com.alertas.shared.dto.PageResponse;
import com.alertas.shared.exception.ApiException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EstadisticaGlobalServiceImpl implements EstadisticaGlobalService {

    private static final int TAMANIO_MAXIMO = 50;

    private static final String[][] ROLES = {
        {"ADMIN", "Administradores"}, {"PSICORIENTADOR", "Psicorientadores"}, {"DOCENTE", "Docentes"},
        {"ESTUDIANTE", "Estudiantes"}};

    private final EstadisticaGlobalRepository repository;
    private final InstitucionRepository institucionRepository;

    public EstadisticaGlobalServiceImpl(EstadisticaGlobalRepository repository, InstitucionRepository institucionRepository) {
        this.repository = repository;
        this.institucionRepository = institucionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public EstadisticasGlobalesResponse resumen(String slug, LocalDate desde, LocalDate hasta) {

        validar(desde, hasta);

        Long institucionId = null;
        if (slug != null && !slug.isBlank()) {
            Institucion institucion = institucionRepository.findBySlug(slug).orElse(null);
            if (institucion == null) {
                throw ApiException.noEncontrado("La institucion no existe");
            }
            institucionId = institucion.getId();
        }

        String zona = ZoneId.systemDefault().getId();

        Object[] fila = repository.resumen(institucionId, desde, hasta, zona);
        ResumenGlobalResponse resumen = new ResumenGlobalResponse(
                numero(fila[0]), numero(fila[1]), numero(fila[2]), numero(fila[3]), numero(fila[4]), numero(fila[5]));

        // roles en orden fijo, tambien los que estan en 0
        Map<String, Long> porRol = new HashMap<>();
        for (Object[] rol : repository.usuariosPorRol(institucionId)) {
            porRol.put((String) rol[0], numero(rol[1]));
        }
        List<ConteoResponse> usuarios = new ArrayList<>();
        for (String[] rol : ROLES) {
            long total = 0;
            if (porRol.containsKey(rol[0])) {
                total = porRol.get(rol[0]);
            }
            usuarios.add(new ConteoResponse(rol[0], rol[1], total));
        }

        List<ConteoResponse> porCategoria = new ArrayList<>();
        for (Object[] categoria : repository.alertasPorCategoria(institucionId, desde, hasta, zona)) {
            porCategoria.add(new ConteoResponse((String) categoria[0], (String) categoria[0], numero(categoria[1])));
        }

        return new EstadisticasGlobalesResponse(
                resumen,
                usuarios,
                alertasPorMes(repository.alertasPorMes(institucionId, desde, hasta, zona)),
                porCategoria,
                smsPorMes(repository.smsPorMes(institucionId, desde, hasta, zona)));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ComparativoResponse> comparativo(LocalDate desde, LocalDate hasta, int pagina, int tamanio) {

        validar(desde, hasta);

        PageRequest pageRequest = PageRequest.of(Math.max(pagina, 0), limitarTamanio(tamanio));
        String zona = ZoneId.systemDefault().getId();

        List<ComparativoResponse> contenido = new ArrayList<>();
        for (Object[] fila : repository.comparativo(desde, hasta, zona, pageRequest.getPageSize(), (int) pageRequest.getOffset())) {
            contenido.add(new ComparativoResponse(
                    (String) fila[0],
                    (String) fila[1],
                    Boolean.TRUE.equals(fila[2]),
                    numero(fila[3]),
                    numero(fila[4]),
                    numero(fila[5]),
                    numero(fila[6]),
                    numero(fila[7]),
                    numero(fila[8]),
                    numero(fila[9])));
        }

        // instituciones no tiene RLS: el total sale directo
        long total = institucionRepository.count();
        return PageResponse.de(contenido, new PageImpl<>(contenido, pageRequest, total));
    }

    // ---------------------------------------------------------------- ayudas

    private void validar(LocalDate desde, LocalDate hasta) {

        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw ApiException.invalido("La fecha inicial no puede ser despues de la final");
        }
    }

    // todos los meses entre el primero y el ultimo; los que no tienen alertas en 0
    private List<ConteoResponse> alertasPorMes(List<Object[]> filas) {

        List<ConteoResponse> respuesta = new ArrayList<>();
        if (filas.isEmpty()) {
            return respuesta;
        }

        Map<String, Long> totales = new HashMap<>();
        for (Object[] fila : filas) {
            totales.put((String) fila[0], numero(fila[1]));
        }

        YearMonth mes = YearMonth.parse((String) filas.get(0)[0]);
        YearMonth ultimo = YearMonth.parse((String) filas.get(filas.size() - 1)[0]);
        while (!mes.isAfter(ultimo)) {
            long total = 0;
            if (totales.containsKey(mes.toString())) {
                total = totales.get(mes.toString());
            }
            respuesta.add(new ConteoResponse(mes.toString(), Fechas.mesCorto(mes), total));
            mes = mes.plusMonths(1);
        }
        return respuesta;
    }

    private List<SmsMesResponse> smsPorMes(List<Object[]> filas) {

        List<SmsMesResponse> respuesta = new ArrayList<>();
        if (filas.isEmpty()) {
            return respuesta;
        }

        Map<String, Object[]> porMes = new HashMap<>();
        for (Object[] fila : filas) {
            porMes.put((String) fila[0], fila);
        }

        YearMonth mes = YearMonth.parse((String) filas.get(0)[0]);
        YearMonth ultimo = YearMonth.parse((String) filas.get(filas.size() - 1)[0]);
        while (!mes.isAfter(ultimo)) {
            Object[] fila = porMes.get(mes.toString());
            if (fila == null) {
                respuesta.add(new SmsMesResponse(mes.toString(), Fechas.mesCorto(mes), 0, 0, 0));
            } else {
                respuesta.add(new SmsMesResponse(mes.toString(), Fechas.mesCorto(mes),
                        numero(fila[1]), numero(fila[2]), numero(fila[3])));
            }
            mes = mes.plusMonths(1);
        }
        return respuesta;
    }

    private int limitarTamanio(int tamanio) {

        if (tamanio < 1) {
            return 20;
        }

        if (tamanio > TAMANIO_MAXIMO) {
            return TAMANIO_MAXIMO;
        }

        return tamanio;
    }

    private long numero(Object valor) {

        if (valor == null) {
            return 0;
        }
        return ((Number) valor).longValue();
    }
}
