package com.alertas.usuario.dto;

import com.alertas.auth.model.Rol;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

// o se manda el rol (todos los de ese rol) o la lista de usuarios seleccionados
public record EstadoMasivoRequest(

        Rol rol,

        @Size(max = 2000, message = "Maximo 2000 usuarios por vez")
        List<Long> usuarioIds,

        @NotNull(message = "Falta indicar si quedan activos o no")
        Boolean activo) {
}
