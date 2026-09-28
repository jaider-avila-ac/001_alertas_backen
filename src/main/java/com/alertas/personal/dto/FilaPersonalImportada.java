package com.alertas.personal.dto;

// una fila del excel ya validada. se guarda en redis mientras el admin confirma
public record FilaPersonalImportada(
        int fila,
        String tipoDoc,
        String nroDoc,
        String nombres,
        String apellidos,
        String rol,
        String correo,
        String celular) {
}
