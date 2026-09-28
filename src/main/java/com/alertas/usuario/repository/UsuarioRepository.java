package com.alertas.usuario.repository;

import com.alertas.auth.model.Rol;
import com.alertas.usuario.model.Usuario;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// RLS ya filtra por institucion, por eso no hace falta pasar el id de la institucion
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByUsuario(String usuario);

    boolean existsByUsuario(String usuario);

    @Query("SELECT u.usuario FROM Usuario u WHERE u.usuario IN :documentos")
    List<String> documentosExistentes(@Param("documentos") Collection<String> documentos);

    // los administradores nunca entran en los cambios masivos, esos los maneja el superadmin
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Usuario u SET u.activo = :activo "
            + "WHERE u.rol = :rol AND u.rol <> com.alertas.auth.model.Rol.ADMIN AND u.activo <> :activo")
    int cambiarEstadoPorRol(@Param("rol") Rol rol, @Param("activo") boolean activo);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Usuario u SET u.activo = :activo "
            + "WHERE u.id IN :ids AND u.rol <> com.alertas.auth.model.Rol.ADMIN AND u.activo <> :activo")
    int cambiarEstadoPorIds(@Param("ids") List<Long> ids, @Param("activo") boolean activo);
}
