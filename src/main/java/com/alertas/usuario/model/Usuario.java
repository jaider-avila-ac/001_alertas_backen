package com.alertas.usuario.model;

import com.alertas.auth.model.Rol;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "usuarios")
@Getter
@Setter
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "usu_id")
    @Setter(AccessLevel.NONE)
    private Long id;

    @Column(name = "usu_ins_id", nullable = false, updatable = false)
    private Long institucionId;

    // numero de documento
    @Column(name = "usu_usuario", nullable = false, length = 20)
    private String usuario;

    @Column(name = "usu_contrasena_hash", nullable = false, length = 100)
    private String contrasenaHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "usu_rol", nullable = false, length = 20)
    private Rol rol;

    @Column(name = "usu_activo", nullable = false)
    private boolean activo = true;

    @Column(name = "usu_debe_cambiar_contrasena", nullable = false)
    private boolean debeCambiarContrasena = true;

    @Column(name = "usu_contrasena_cambiada_en")
    private OffsetDateTime contrasenaCambiadaEn;

    @Column(name = "usu_ultimo_ingreso")
    private OffsetDateTime ultimoIngreso;

    @Column(name = "usu_creado_en", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private OffsetDateTime creadoEn;

    @Column(name = "usu_actualizado_en", nullable = false)
    @Setter(AccessLevel.NONE)
    private OffsetDateTime actualizadoEn;

    @PrePersist
    void alCrear() {

        creadoEn = OffsetDateTime.now();
        actualizadoEn = creadoEn;
    }

    @PreUpdate
    void alActualizar() {
        actualizadoEn = OffsetDateTime.now();
    }
}
