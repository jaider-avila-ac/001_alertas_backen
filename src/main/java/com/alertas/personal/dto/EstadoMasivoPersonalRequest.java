package com.alertas.personal.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

// una sola forma: los seleccionados (codigos), todos los de un rol o todos
public record EstadoMasivoPersonalRequest(

        @Size(max = 2000, message = "Maximo 2000 personas por vez")
        List<String> codigos,

        @Pattern(regexp = "DOCENTE|PSICORIENTADOR", message = "El rol debe ser docente o psicorientador")
        String rol,

        boolean todos,

        @NotNull(message = "Falta indicar si quedan activos o no")
        Boolean activo) {
}
