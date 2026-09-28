package com.alertas.institucion.repository;

import com.alertas.institucion.model.Institucion;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InstitucionRepository extends JpaRepository<Institucion, Long> {

    Optional<Institucion> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Long id);

    boolean existsByCodigoDane(String codigoDane);

    boolean existsByCodigoDaneAndIdNot(String codigoDane, Long id);

    // busqueda sin tildes por nombre o slug. los CAST son para que postgres acepte los null
    @Query(value = """
            SELECT * FROM instituciones i
            WHERE (CAST(:texto AS text) IS NULL
                   OR lower(f_unaccent(i.ins_nombre || ' ' || i.ins_slug)) LIKE '%' || lower(f_unaccent(CAST(:texto AS text))) || '%')
              AND (CAST(:activa AS boolean) IS NULL OR i.ins_activa = CAST(:activa AS boolean))
            ORDER BY i.ins_nombre
            """,
            countQuery = """
            SELECT count(*) FROM instituciones i
            WHERE (CAST(:texto AS text) IS NULL
                   OR lower(f_unaccent(i.ins_nombre || ' ' || i.ins_slug)) LIKE '%' || lower(f_unaccent(CAST(:texto AS text))) || '%')
              AND (CAST(:activa AS boolean) IS NULL OR i.ins_activa = CAST(:activa AS boolean))
            """,
            nativeQuery = true)
    Page<Institucion> buscar(@Param("texto") String texto, @Param("activa") Boolean activa, Pageable pageable);
}
