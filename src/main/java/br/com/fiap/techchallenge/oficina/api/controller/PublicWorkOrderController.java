package br.com.fiap.techchallenge.oficina.api.controller;

import br.com.fiap.techchallenge.oficina.api.dto.workorder.PublicWorkOrderStatusResponse;
import br.com.fiap.techchallenge.oficina.application.service.WorkOrderApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/work-orders")
public class PublicWorkOrderController {

    private final WorkOrderApplicationService service;

    public PublicWorkOrderController(WorkOrderApplicationService service) {
        this.service = service;
    }

    @GetMapping("/{code}/status")
    @Operation(summary = "Consulta pública de andamento da OS por código e CPF/CNPJ")
    public PublicWorkOrderStatusResponse status(@PathVariable String code, @RequestParam String document) {
        return service.publicStatus(code, document);
    }
}
