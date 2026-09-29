package com.alertas.institucion.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "instituciones")
@Getter
@Setter
public class Institucion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ins_id")
    @Setter(AccessLevel.NONE)
    private Long id;

    @Column(name = "ins_nombre", nullable = false, length = 150)
    private String nombre;

    @Column(name = "ins_slug", nullable = false, length = 60)
    private String slug;

    @Column(name = "ins_codigo_dane", length = 20)
    private String codigoDane;

    @Column(name = "ins_municipio", length = 80)
    private String municipio;

    @Column(name = "ins_departamento", length = 80)
    private String departamento;

    @Column(name = "ins_direccion", length = 150)
    private String direccion;

    @Column(name = "ins_telefono", length = 20)
    private String telefono;

    @Column(name = "ins_correo", length = 120)
    private String correo;

    // se cambia solo con activar() e inactivar(), para que siempre vaya con fecha y motivo
    @Column(name = "ins_activa", nullable = false)
    @Setter(AccessLevel.NONE)
    private boolean activa = true;

    @Column(name = "ins_acceso_estudiantes", nullable = false)
    private boolean accesoEstudiantes = true;

    @Column(name = "ins_sms_activo", nullable = false)
    private boolean smsActivo = false;

    // valoraciones de rutina: las enciende el admin del colegio
    @Column(name = "ins_valoraciones_activas", nullable = false)
    private boolean valoracionesActivas = false;

    // cada cuantos dias le toca de nuevo a un estudiante
    @Column(name = "ins_valoraciones_dias", nullable = false)
    private int valoracionesDias = 180;

    @Column(name = "ins_inactivada_en")
    @Setter(AccessLevel.NONE)
    private OffsetDateTime inactivadaEn;

    @Column(name = "ins_motivo_inactivacion", length = 300)
    @Setter(AccessLevel.NONE)
    private String motivoInactivacion;

    @Column(name = "ins_creado_en", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private OffsetDateTime creadoEn;

    @Column(name = "ins_actualizado_en", nullable = false)
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

    public void inactivar(String motivo) {

        activa = false;
        inactivadaEn = OffsetDateTime.now();
        motivoInactivacion = motivo;
    }

    public void activar() {

        activa = true;
        inactivadaEn = null;
        motivoInactivacion = null;
    }
}
