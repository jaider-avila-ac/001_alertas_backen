package com.alertas.personal.repository;

import com.alertas.auth.model.Rol;
import com.alertas.personal.model.Personal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PersonalRepository extends JpaRepository<Personal, Long> {

    boolean existsByNroDoc(String nroDoc);

    @Query("SELECT p FROM Personal p JOIN FETCH p.usuario u WHERE u.rol = :rol ORDER BY p.apellidos, p.nombres")
    List<Personal> buscarPorRol(@Param("rol") Rol rol);

    @Query("SELECT p FROM Personal p JOIN FETCH p.usuario u WHERE u.id = :usuarioId")
    Personal buscarPorUsuario(@Param("usuarioId") Long usuarioId);
}
