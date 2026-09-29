package com.alertas.notificacion.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

// aviso dentro de la app para un usuario. el enlace es la ruta del front sin el slug
@Entity
@Table(name = "notificaciones")
@Getter
@Setter
public class Notificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "not_id")
    @Setter(AccessLevel.NONE)
    private Long id;

    @Column(name = "not_ins_id", nullable = false, updatable = false)
    private Long institucionId;

    @Column(name = "not_usu_id", nullable = false, updatable = false)
    private Long usuarioId;

    @Column(name = "not_tipo", nullable = false, updatable = false, length = 40)
    private String tipo;

    @Column(name = "not_titulo", nullable = false, updatable = false, length = 120)
    private String titulo;

    @Column(name = "not_mensaje", nullable = false, updatable = false, length = 300)
    private String mensaje;

    @Column(name = "not_enlace", updatable = false, length = 200)
    private String enlace;

    @Column(name = "not_leida", nullable = false)
    private boolean leida;

    // id del mensaje en la cola: si se reintenta, no se guarda dos veces
    @Column(name = "not_cola_id", updatable = false, length = 40)
    private String colaId;

    @Column(name = "not_creado_en", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private OffsetDateTime creadoEn;

    @PrePersist
    void alCrear() {
        creadoEn = OffsetDateTime.now();
    }
}
