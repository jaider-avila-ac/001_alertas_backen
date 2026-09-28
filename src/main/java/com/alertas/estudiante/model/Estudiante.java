package com.alertas.estudiante.model;

import com.alertas.usuario.model.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "estudiantes")
@Getter
@Setter
public class Estudiante {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "est_id")
    @Setter(AccessLevel.NONE)
    private Long id;

    @Column(name = "est_ins_id", nullable = false, updatable = false)
    private Long institucionId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "est_usu_id", nullable = false, updatable = false)
    private Usuario usuario;

    // lo que ve el front en vez del id
    @Column(name = "est_codigo", nullable = false, updatable = false, length = 16)
    private String codigo;

    // el del qr, distinto al codigo para poder cambiarlo si se pierde un carne
    @Column(name = "est_codigo_qr", nullable = false, length = 16)
    private String codigoQr;

    @Column(name = "est_tipo_doc", nullable = false, length = 3)
    private String tipoDoc;

    @Column(name = "est_nro_doc", nullable = false, length = 20)
    private String nroDoc;

    @Column(name = "est_nombres", nullable = false, length = 80)
    private String nombres;

    @Column(name = "est_apellidos", nullable = false, length = 80)
    private String apellidos;

    @Column(name = "est_genero", length = 1)
    private String genero;

    @Column(name = "est_fecha_nacimiento")
    private LocalDate fechaNacimiento;

    @Column(name = "est_celular", length = 10)
    private String celular;

    @Column(name = "est_correo", length = 120)
    private String correo;

    @Column(name = "est_direccion", length = 150)
    private String direccion;

    @Column(name = "est_barrio", length = 80)
    private String barrio;

    // salud, para emergencias. solo lo ven el admin y el psicorientador
    @Column(name = "est_eps", length = 80)
    private String eps;

    @Column(name = "est_rh", length = 3)
    private String rh;

    @Column(name = "est_condiciones_salud", length = 500)
    private String condicionesSalud;

    @Column(name = "est_sms_familiares", nullable = false)
    private boolean smsFamiliares = true;

    @Column(name = "est_creado_en", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private OffsetDateTime creadoEn;

    @Column(name = "est_actualizado_en", nullable = false)
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
