package com.alertas.matricula.service;

import com.alertas.estructura.dto.GrupoResponse;
import com.alertas.matricula.dto.MatriculaResponse;
import com.alertas.matricula.model.Matricula;
import java.util.Collection;
import java.util.List;

// matriculas del estudiante: una por anio, con su historial de movimientos
public interface MatriculaService {

    // deja al estudiante en el grupo (el anio es el del grupo).
    // si no tiene matricula ese anio la crea (NUEVA, PROMOCION o REPITE segun el anio anterior);
    // si ya tiene, la mueve y deja el movimiento. devuelve true si estaba retirada y se reabrio
    boolean ubicar(Long estudianteId, GrupoResponse grupo, String motivo);

    // cierra como RETIRADA las matriculas activas del estudiante. devuelve cuantas cerro
    int retirar(Long estudianteId, String motivo);

    // los que no siguieron al anio nuevo: sus matriculas activas quedan RETIRADA
    int cerrarActivas(Collection<Long> estudianteIds, String motivo);

    // del anio mas reciente al mas viejo, con sus movimientos
    List<MatriculaResponse> trayectoria(Long estudianteId);

    // la del anio activo, null si no tiene. la usan alertas y citas para guardar el grupo del momento
    Matricula delAnioActivo(Long estudianteId);
}
