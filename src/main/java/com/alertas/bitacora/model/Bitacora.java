package com.alertas.bitacora.model;

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

// solo se inserta, la bd no deja modificarla
@Entity
@Table(name = "bitacora")
@Getter
@Setter
public class Bitacora {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "bit_id")
    @Setter(AccessLevel.NONE)
    private Long id;

    @Column(name = "bit_ins_id", nullable = false, updatable = false)
    private Long institucionId;

    // o lo hizo un usuario de la institucion o un superadmin
    @Column(name = "bit_usu_id", updatable = false)
    private Long usuarioId;

    @Column(name = "bit_sad_id", updatable = false)
    private Long superadminId;

    @Column(name = "bit_accion", nullable = false, length = 60, updatable = false)
    private String accion;

    @Column(name = "bit_entidad", nullable = false, length = 40, updatable = false)
    private String entidad;

    @Column(name = "bit_entidad_id", updatable = false)
    private Long entidadId;

    @Column(name = "bit_detalle", updatable = false)
    private String detalle;

    @Column(name = "bit_ip", length = 45, updatable = false)
    private String ip;

    @Column(name = "bit_creado_en", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private OffsetDateTime creadoEn;

    @PrePersist
    void alCrear() {
        creadoEn = OffsetDateTime.now();
    }
}
