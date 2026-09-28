package com.alertas.estudiante.repository;

import com.alertas.estudiante.model.Estudiante;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// RLS ya filtra por institucion
public interface EstudianteRepository extends JpaRepository<Estudiante, Long> {

    @Query("SELECT e FROM Estudiante e JOIN FETCH e.usuario WHERE e.codigo = :codigo")
    Estudiante buscarPorCodigo(@Param("codigo") String codigo);

    @Query("SELECT e FROM Estudiante e JOIN FETCH e.usuario WHERE e.codigoQr = :codigoQr")
    Estudiante buscarPorCodigoQr(@Param("codigoQr") String codigoQr);

    @Query("SELECT e FROM Estudiante e JOIN FETCH e.usuario u WHERE u.id = :usuarioId")
    Estudiante buscarPorUsuario(@Param("usuarioId") Long usuarioId);

    boolean existsByNroDoc(String nroDoc);

    boolean existsByNroDocAndIdNot(String nroDoc, Long id);

    @Query("SELECT e FROM Estudiante e JOIN FETCH e.usuario WHERE e.nroDoc IN :documentos")
    List<Estudiante> buscarPorDocumentos(@Param("documentos") Collection<String> documentos);

    // estudiantes de un grupo, para imprimir sus qr. los retirados no
    @Query("SELECT e FROM Estudiante e, Matricula m WHERE m.estudianteId = e.id AND m.grupoId = :grupoId "
            + "AND m.estado <> 'RETIRADA' ORDER BY e.apellidos, e.nombres")
    List<Estudiante> buscarPorGrupo(@Param("grupoId") Long grupoId);

    // listado con filtros. todo opcional; los CAST son para que postgres acepte los null
    @Query(value = """
            SELECT e.est_codigo AS codigo, e.est_tipo_doc AS tipoDoc, e.est_nro_doc AS nroDoc,
                   e.est_nombres AS nombres, e.est_apellidos AS apellidos, u.usu_activo AS activo,
                   g.grp_id AS grupoId, gr.gra_nombre AS gradoNombre, gr.gra_orden AS gradoOrden,
                   g.grp_nombre AS grupoNombre
            FROM estudiantes e
            JOIN usuarios u ON u.usu_id = e.est_usu_id
            LEFT JOIN matriculas ub ON ub.mat_est_id = e.est_id AND ub.mat_anl_id = :anioId
            LEFT JOIN grupos g ON g.grp_id = ub.mat_grp_id
            LEFT JOIN grados gr ON gr.gra_id = g.grp_gra_id
            WHERE (CAST(:texto AS text) IS NULL
                   OR e.est_busqueda LIKE '%' || lower(f_unaccent(CAST(:texto AS text))) || '%')
              AND (CAST(:gradoId AS bigint) IS NULL OR g.grp_gra_id = CAST(:gradoId AS bigint))
              AND (CAST(:grupoId AS bigint) IS NULL OR ub.mat_grp_id = CAST(:grupoId AS bigint))
              AND (CAST(:activo AS boolean) IS NULL OR u.usu_activo = CAST(:activo AS boolean))
              AND (:sinGrupo = false OR ub.mat_id IS NULL)
            ORDER BY e.est_apellidos, e.est_nombres
            """,
            countQuery = """
            SELECT count(*)
            FROM estudiantes e
            JOIN usuarios u ON u.usu_id = e.est_usu_id
            LEFT JOIN matriculas ub ON ub.mat_est_id = e.est_id AND ub.mat_anl_id = :anioId
            LEFT JOIN grupos g ON g.grp_id = ub.mat_grp_id
            WHERE (CAST(:texto AS text) IS NULL
                   OR e.est_busqueda LIKE '%' || lower(f_unaccent(CAST(:texto AS text))) || '%')
              AND (CAST(:gradoId AS bigint) IS NULL OR g.grp_gra_id = CAST(:gradoId AS bigint))
              AND (CAST(:grupoId AS bigint) IS NULL OR ub.mat_grp_id = CAST(:grupoId AS bigint))
              AND (CAST(:activo AS boolean) IS NULL OR u.usu_activo = CAST(:activo AS boolean))
              AND (:sinGrupo = false OR ub.mat_id IS NULL)
            """,
            nativeQuery = true)
    Page<EstudianteFila> buscar(
            @Param("anioId") Long anioId,
            @Param("texto") String texto,
            @Param("gradoId") Long gradoId,
            @Param("grupoId") Long grupoId,
            @Param("activo") Boolean activo,
            @Param("sinGrupo") boolean sinGrupo,
            Pageable pageable);

    // ---- ids de usuario para activar o inactivar en bloque (uso interno, no salen del backend) ----

    @Query("SELECT e.usuario.id FROM Estudiante e WHERE e.codigo IN :codigos")
    List<Long> usuarioIdsPorCodigos(@Param("codigos") Collection<String> codigos);

    @Query(value = "SELECT e.est_usu_id FROM estudiantes e JOIN matriculas m ON m.mat_est_id = e.est_id "
            + "WHERE m.mat_grp_id = :grupoId", nativeQuery = true)
    List<Long> usuarioIdsPorGrupo(@Param("grupoId") Long grupoId);

    @Query(value = "SELECT e.est_usu_id FROM estudiantes e JOIN matriculas m ON m.mat_est_id = e.est_id "
            + "JOIN grupos g ON g.grp_id = m.mat_grp_id WHERE m.mat_anl_id = :anioId AND g.grp_gra_id = :gradoId",
            nativeQuery = true)
    List<Long> usuarioIdsPorGrado(@Param("anioId") Long anioId, @Param("gradoId") Long gradoId);

    // activos sin matricula en el anio (se retiraron o se graduaron). devuelve [id estudiante, id usuario]
    @Query(value = "SELECT e.est_id, e.est_usu_id FROM estudiantes e JOIN usuarios u ON u.usu_id = e.est_usu_id "
            + "WHERE u.usu_activo AND NOT EXISTS "
            + "(SELECT 1 FROM matriculas m WHERE m.mat_est_id = e.est_id AND m.mat_anl_id = :anioId)",
            nativeQuery = true)
    List<Object[]> sinMatricula(@Param("anioId") Long anioId);
}
