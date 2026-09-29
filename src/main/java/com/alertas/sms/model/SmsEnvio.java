package com.alertas.sms.model;

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

// un sms: queda pendiente en la transaccion que lo origina y se envia despues. sirve para ver el costo por colegio
@Entity
@Table(name = "sms_envios")
@Getter
@Setter
public class SmsEnvio {

    public static final String PENDIENTE = "PENDIENTE";
    public static final String ENVIADO = "ENVIADO";
    public static final String FALLIDO = "FALLIDO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sms_id")
    @Setter(AccessLevel.NONE)
    private Long id;

    @Column(name = "sms_ins_id", nullable = false, updatable = false)
    private Long institucionId;

    // ALERTA_CREADA, CITA_AGENDADA, CITA_REPROGRAMADA o CITA_CANCELADA
    @Column(name = "sms_evento", nullable = false, updatable = false, length = 20)
    private String evento;

    // ESTUDIANTE o FAMILIAR
    @Column(name = "sms_destinatario", nullable = false, updatable = false, length = 12)
    private String destinatario;

    @Column(name = "sms_celular", nullable = false, updatable = false, length = 10)
    private String celular;

    @Column(name = "sms_mensaje", nullable = false, updatable = false, length = 480)
    private String mensaje;

    @Column(name = "sms_segmentos", nullable = false, updatable = false)
    private int segmentos;

    @Column(name = "sms_estado", nullable = false, length = 10)
    private String estado = PENDIENTE;

    @Column(name = "sms_error", length = 300)
    private String error;

    // id del mensaje en el proveedor
    @Column(name = "sms_proveedor_id", length = 64)
    private String proveedorId;

    @Column(name = "sms_ale_id", updatable = false)
    private Long alertaId;

    @Column(name = "sms_cit_id", updatable = false)
    private Long citaId;

    @Column(name = "sms_creado_en", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private OffsetDateTime creadoEn;

    @Column(name = "sms_enviado_en")
    private OffsetDateTime enviadoEn;

    @PrePersist
    void alCrear() {
        creadoEn = OffsetDateTime.now();
    }
}
