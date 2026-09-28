package com.alertas.estructura.service;

import com.alertas.estructura.dto.AnioLectivoResponse;
import com.alertas.estructura.dto.GradoResponse;
import com.alertas.estructura.dto.GrupoResponse;
import java.util.List;

// anios lectivos, grados y grupos de la institucion del contexto
public interface EstructuraService {

    // grados de prejardin a once y el anio actual como activo. se usa al crear la institucion
    void crearDatosIniciales();

    // ---- grados ----

    List<GradoResponse> listarGrados();

    GradoResponse cambiarEstadoGrado(Long gradoId, boolean activo);

    // ---- anios lectivos ----

    List<AnioLectivoResponse> listarAnios();

    AnioLectivoResponse crearAnio(int anio);

    // el que estaba activo deja de estarlo
    AnioLectivoResponse activarAnio(Long anioId);

    void borrarAnio(Long anioId);

    // null si la institucion no tiene anio activo
    AnioLectivoResponse anioActivo();

    // falla si el anio ya paso (sus grupos y matriculas son historial)
    AnioLectivoResponse buscarAnioEditable(Long anioId);

    // ---- grupos ----

    // anioId null = el anio activo. son pocos por anio, no se pagina
    List<GrupoResponse> listarGrupos(Long anioId);

    GrupoResponse crearGrupo(Long anioId, Long gradoId, String nombre);

    GrupoResponse renombrarGrupo(Long grupoId, String nombre);

    void borrarGrupo(Long grupoId);

    // falla si no existe en la institucion
    GrupoResponse buscarGrupo(Long grupoId);
}
