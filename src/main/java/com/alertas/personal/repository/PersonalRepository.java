package com.alertas.personal.repository;

import com.alertas.auth.model.Rol;
import com.alertas.personal.model.Personal;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// RLS ya filtra por institucion
public interface PersonalRepository extends JpaRepository<Personal, Long> {

    boolean existsByNroDoc(String nroDoc);

    // estudiantes con alertas activas que atiende este psicorientador
    @Query(value = "SELECT count(DISTINCT a.ale_est_id) FROM alertas a "
            + "WHERE a.ale_psi_id = :personalId AND a.ale_estado IN ('PENDIENTE', 'EN_PROCESO')", nativeQuery = true)
    long casosAbiertos(@Param("personalId") Long personalId);

    boolean existsByNroDocAndIdNot(String nroDoc, Long id);

    @Query("SELECT p FROM Personal p JOIN FETCH p.usuario u WHERE u.rol = :rol ORDER BY p.apellidos, p.nombres")
    List<Personal> buscarPorRol(@Param("rol") Rol rol);

    @Query("SELECT p FROM Personal p JOIN FETCH p.usuario u WHERE u.id = :usuarioId")
    Personal buscarPorUsuario(@Param("usuarioId") Long usuarioId);

    @Query("SELECT p FROM Personal p JOIN FETCH p.usuario u WHERE p.codigo = :codigo")
    Personal buscarPorCodigo(@Param("codigo") String codigo);

    @Query("SELECT p FROM Personal p JOIN FETCH p.usuario WHERE p.nroDoc IN :documentos")
    List<Personal> buscarPorDocumentos(@Param("documentos") Collection<String> documentos);

    // docentes y psicorientadores (los administradores los maneja el superadmin).
    // filtros opcionales; los CAST son para que postgres acepte los null
    @Query(value = """
            SELECT p.per_codigo AS codigo, p.per_tipo_doc AS tipoDoc, p.per_nro_doc AS nroDoc,
                   p.per_nombres AS nombres, p.per_apellidos AS apellidos, p.per_correo AS correo,
                   u.usu_rol AS rol, u.usu_activo AS activo
            FROM personal p
            JOIN usuarios u ON u.usu_id = p.per_usu_id
            WHERE u.usu_rol IN ('DOCENTE', 'PSICORIENTADOR')
              AND (CAST(:rol AS text) IS NULL OR u.usu_rol = CAST(:rol AS text))
              AND (CAST(:texto AS text) IS NULL
                   OR p.per_busqueda LIKE '%' || lower(f_unaccent(CAST(:texto AS text))) || '%')
              AND (CAST(:activo AS boolean) IS NULL OR u.usu_activo = CAST(:activo AS boolean))
            ORDER BY p.per_apellidos, p.per_nombres
            """,
            countQuery = """
            SELECT count(*)
            FROM personal p
            JOIN usuarios u ON u.usu_id = p.per_usu_id
            WHERE u.usu_rol IN ('DOCENTE', 'PSICORIENTADOR')
              AND (CAST(:rol AS text) IS NULL OR u.usu_rol = CAST(:rol AS text))
              AND (CAST(:texto AS text) IS NULL
                   OR p.per_busqueda LIKE '%' || lower(f_unaccent(CAST(:texto AS text))) || '%')
              AND (CAST(:activo AS boolean) IS NULL OR u.usu_activo = CAST(:activo AS boolean))
            """,
            nativeQuery = true)
    Page<PersonalFila> buscar(
            @Param("texto") String texto,
            @Param("rol") String rol,
            @Param("activo") Boolean activo,
            Pageable pageable);

    // ids de usuario para activar o inactivar en bloque (uso interno, no salen del backend).
    // los administradores nunca entran
    @Query("SELECT p.usuario.id FROM Personal p WHERE p.codigo IN :codigos "
            + "AND p.usuario.rol <> com.alertas.auth.model.Rol.ADMIN")
    List<Long> usuarioIdsPorCodigos(@Param("codigos") Collection<String> codigos);
}
