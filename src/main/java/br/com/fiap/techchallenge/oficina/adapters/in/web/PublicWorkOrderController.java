package br.com.fiap.techchallenge.oficina.adapters.in.web;

import br.com.fiap.techchallenge.oficina.adapters.in.web.dto.workorder.PublicWorkOrderStatusResponse;
import br.com.fiap.techchallenge.oficina.adapters.in.web.mapper.WebDtoMapper;
import br.com.fiap.techchallenge.oficina.application.port.in.GetWorkOrderUseCase;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/work-orders")
public class PublicWorkOrderController {

    private final GetWorkOrderUseCase service;

    public PublicWorkOrderController(GetWorkOrderUseCase service) {
        this.service = service;
    }

    @GetMapping("/{code}/status")
    @Operation(summary = "Consulta pública de andamento da OS por código e CPF/CNPJ")
    public PublicWorkOrderStatusResponse status(@PathVariable String code, @RequestParam String document) {
        return WebDtoMapper.toPublicResponse(service.getPublicStatus(code, document));
    }
}
