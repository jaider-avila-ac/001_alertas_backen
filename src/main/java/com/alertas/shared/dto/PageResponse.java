package com.alertas.shared.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public record PageResponse<T>(
        List<T> contenido,
        int pagina,
        int tamanio,
        long totalElementos,
        int totalPaginas) {

    // contenido ya convertido a Response, lo demas se saca de la pagina
    public static <T> PageResponse<T> de(List<T> contenido, Page<?> page) {

        return new PageResponse<>(
                contenido,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
