package br.com.fiap.techchallenge.oficina.adapters.in.web;

import br.com.fiap.techchallenge.oficina.adapters.in.web.dto.workorder.PublicWorkOrderStatusResponse;
import br.com.fiap.techchallenge.oficina.adapters.in.web.mapper.WebDtoMapper;
import br.com.fiap.techchallenge.oficina.application.port.in.GetWorkOrderUseCase;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Deprecated(forRemoval = true)
@Profile("local & !prod & !production")
@RestController
@RequestMapping("/api/v1/public/work-orders")
public class LegacyPublicWorkOrderController {
    private final GetWorkOrderUseCase service;

    public LegacyPublicWorkOrderController(GetWorkOrderUseCase service) {
        this.service = service;
    }

    @Deprecated(forRemoval = true)
    @GetMapping("/{code}/status")
    @Operation(
        summary = "[DEPRECATED/local only] Consulta pública com documento na query string",
        deprecated = true
    )
    public PublicWorkOrderStatusResponse status(@PathVariable String code, @RequestParam String document) {
        return WebDtoMapper.toPublicResponse(service.getPublicStatus(code, document));
    }
}
