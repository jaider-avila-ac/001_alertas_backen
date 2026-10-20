package com.alertas.estudiante.repository;

import com.alertas.estudiante.model.Estudiante;
import com.alertas.shared.RangoEdad;
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

    // estudiantes activos para imprimir sus qr, por paginas. edad null = todos los rangos
    @Query(value = """
            SELECT e.* FROM estudiantes e
            JOIN usuarios u ON u.usu_id = e.est_usu_id
            WHERE u.usu_activo
              AND (CAST(:edad AS text) IS NULL OR (""" + RangoEdad.SQL_HOY + """
            ) = CAST(:edad AS text))
            ORDER BY e.est_apellidos, e.est_nombres
            """,
            countQuery = """
            SELECT count(*) FROM estudiantes e
            JOIN usuarios u ON u.usu_id = e.est_usu_id
            WHERE u.usu_activo
              AND (CAST(:edad AS text) IS NULL OR (""" + RangoEdad.SQL_HOY + """
            ) = CAST(:edad AS text))
            """,
            nativeQuery = true)
    Page<Estudiante> activosParaQr(@Param("edad") String edad, Pageable pageable);

    // cuantos activos hay en cada rango de edad: {clave, total}
    @Query(value = "SELECT (" + RangoEdad.SQL_HOY + ") AS clave, count(*) AS total FROM estudiantes e "
            + "JOIN usuarios u ON u.usu_id = e.est_usu_id WHERE u.usu_activo GROUP BY 1", nativeQuery = true)
    List<Object[]> activosPorRango();

    // los grupos que se han escrito, para los filtros
    @Query(value = "SELECT DISTINCT e.est_grupo FROM estudiantes e WHERE e.est_grupo IS NOT NULL ORDER BY 1",
            nativeQuery = true)
    List<String> grupos();

    // listado con filtros. todo opcional; los CAST son para que postgres acepte los null
    @Query(value = """
            SELECT e.est_codigo AS codigo, e.est_tipo_doc AS tipoDoc, e.est_nro_doc AS nroDoc,
                   e.est_nombres AS nombres, e.est_apellidos AS apellidos, u.usu_activo AS activo,
                   e.est_grupo AS grupo, e.est_fecha_nacimiento AS fechaNacimiento
            FROM estudiantes e
            JOIN usuarios u ON u.usu_id = e.est_usu_id
            WHERE (CAST(:texto AS text) IS NULL
                   OR e.est_busqueda LIKE '%' || lower(f_unaccent(CAST(:texto AS text))) || '%')
              AND (CAST(:grupo AS text) IS NULL OR lower(e.est_grupo) = lower(CAST(:grupo AS text)))
              AND (CAST(:edad AS text) IS NULL OR (""" + RangoEdad.SQL_HOY + """
            ) = CAST(:edad AS text))
              AND (CAST(:activo AS boolean) IS NULL OR u.usu_activo = CAST(:activo AS boolean))
            ORDER BY e.est_apellidos, e.est_nombres
            """,
            countQuery = """
            SELECT count(*)
            FROM estudiantes e
            JOIN usuarios u ON u.usu_id = e.est_usu_id
            WHERE (CAST(:texto AS text) IS NULL
                   OR e.est_busqueda LIKE '%' || lower(f_unaccent(CAST(:texto AS text))) || '%')
              AND (CAST(:grupo AS text) IS NULL OR lower(e.est_grupo) = lower(CAST(:grupo AS text)))
              AND (CAST(:edad AS text) IS NULL OR (""" + RangoEdad.SQL_HOY + """
            ) = CAST(:edad AS text))
              AND (CAST(:activo AS boolean) IS NULL OR u.usu_activo = CAST(:activo AS boolean))
            """,
            nativeQuery = true)
    Page<EstudianteFila> buscar(
            @Param("texto") String texto,
            @Param("grupo") String grupo,
            @Param("edad") String edad,
            @Param("activo") Boolean activo,
            Pageable pageable);

    // ---- ids de usuario para activar o inactivar en bloque (uso interno, no salen del backend) ----

    @Query("SELECT e.usuario.id FROM Estudiante e WHERE e.codigo IN :codigos")
    List<Long> usuarioIdsPorCodigos(@Param("codigos") Collection<String> codigos);

    @Query("SELECT e.usuario.id FROM Estudiante e WHERE lower(e.grupo) = lower(:grupo)")
    List<Long> usuarioIdsPorGrupo(@Param("grupo") String grupo);

    @Query(value = "SELECT e.est_usu_id FROM estudiantes e WHERE (" + RangoEdad.SQL_HOY + ") = :edad",
            nativeQuery = true)
    List<Long> usuarioIdsPorEdad(@Param("edad") String edad);

}
