package br.com.fiap.techchallenge.oficina.application.usecase;

import br.com.fiap.techchallenge.oficina.application.port.in.ExternalBudgetDecisionUseCase;
import br.com.fiap.techchallenge.oficina.application.port.in.result.PublicWorkOrderStatusResult;
import br.com.fiap.techchallenge.oficina.application.port.out.PartRepositoryPort;
import br.com.fiap.techchallenge.oficina.application.port.out.IdempotencyPort;
import br.com.fiap.techchallenge.oficina.application.port.out.IdempotencyRecord;
import br.com.fiap.techchallenge.oficina.application.port.out.TransactionPort;
import br.com.fiap.techchallenge.oficina.application.port.out.WorkOrderRepositoryPort;
import br.com.fiap.techchallenge.oficina.domain.exception.BusinessException;
import br.com.fiap.techchallenge.oficina.domain.exception.ConflictException;
import br.com.fiap.techchallenge.oficina.domain.exception.NotFoundException;
import br.com.fiap.techchallenge.oficina.domain.model.catalog.Part;
import br.com.fiap.techchallenge.oficina.domain.model.workorder.WorkOrder;
import br.com.fiap.techchallenge.oficina.domain.service.DocumentValidator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.UUID;

import static br.com.fiap.techchallenge.oficina.application.usecase.mapper.ApplicationResultMapper.toPublicStatusResult;

public final class ExternalBudgetDecisionService implements ExternalBudgetDecisionUseCase {

    private static final String APPROVE_OPERATION = "PUBLIC_BUDGET_APPROVE";
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 160;

    private static final String NOT_FOUND_MESSAGE =
        "Ordem de serviço não encontrada para o documento informado.";

    private final WorkOrderRepositoryPort workOrders;
    private final PartRepositoryPort parts;
    private final IdempotencyPort idempotency;
    private final TransactionPort transactions;

    public ExternalBudgetDecisionService(
        WorkOrderRepositoryPort workOrders,
        PartRepositoryPort parts,
        IdempotencyPort idempotency,
        TransactionPort transactions
    ) {
        this.workOrders = workOrders;
        this.parts = parts;
        this.idempotency = idempotency;
        this.transactions = transactions;
    }

    @Override
    public PublicWorkOrderStatusResult approve(Command command) {
        String idempotencyKey = normalizeIdempotencyKey(command.idempotencyKey());
        if (idempotencyKey == null) {
            return approveWithoutIdempotency(command);
        }

        String normalizedDocument = DocumentValidator.onlyDigits(command.document());
        String requestHash = requestHash(command.code(), normalizedDocument);
        return transactions.required(() -> {
            boolean created = idempotency.tryCreateProcessing(
                APPROVE_OPERATION, idempotencyKey, requestHash, command.code()
            );
            if (!created) {
                return handleExisting(command.code(), normalizedDocument, idempotencyKey, requestHash);
            }

            var record = idempotency.findByOperationAndKeyForUpdate(APPROVE_OPERATION, idempotencyKey)
                .orElseThrow(() -> new IllegalStateException("Registro de idempotência recém-criado não encontrado."));
            var order = findAuthorizedOrderForUpdate(command);
            order.approveBudget();
            saveParts(order);
            var saved = workOrders.save(order);
            idempotency.markCompleted(record.id(), saved.getCode());
            return toPublicStatusResult(saved);
        });
    }

    private PublicWorkOrderStatusResult approveWithoutIdempotency(Command command) {
        return transactions.required(() -> {
            var order = findAuthorizedOrderForUpdate(command);
            order.approveBudget();
            saveParts(order);
            return toPublicStatusResult(workOrders.save(order));
        });
    }

    private PublicWorkOrderStatusResult handleExisting(
        String code,
        String normalizedDocument,
        String idempotencyKey,
        String requestHash
    ) {
        IdempotencyRecord record = idempotency
            .findByOperationAndKeyForUpdate(APPROVE_OPERATION, idempotencyKey)
            .orElseThrow(() -> new IllegalStateException("Registro de idempotência concorrente não encontrado."));
        if (!record.requestHash().equals(requestHash)) {
            throw new ConflictException("Idempotency-Key reutilizada com uma requisição diferente.");
        }
        if (record.status() == IdempotencyRecord.Status.PROCESSING) {
            throw new ConflictException("Requisição idempotente já está em processamento.");
        }
        if (record.status() == IdempotencyRecord.Status.FAILED) {
            throw new ConflictException("Requisição idempotente anterior falhou e não pode ser repetida com a mesma chave.");
        }

        var order = workOrders.findByCode(code)
            .orElseThrow(() -> new NotFoundException(NOT_FOUND_MESSAGE));
        ensureDocumentMatches(order, normalizedDocument);
        return toPublicStatusResult(order);
    }

    private static String normalizeIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String normalized = key.trim();
        if (normalized.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new BusinessException("Idempotency-Key deve ter no máximo 160 caracteres.");
        }
        return normalized;
    }

    private static String requestHash(String code, String normalizedDocument) {
        String canonicalRequest = APPROVE_OPERATION + ":" + code + ":" + normalizedDocument;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(canonicalRequest.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 não disponível na JVM.", exception);
        }
    }

    @Override
    public PublicWorkOrderStatusResult reject(Command command) {
        return transactions.required(() -> {
            var order = findAuthorizedOrderForUpdate(command);
            order.rejectBudget();
            return toPublicStatusResult(workOrders.save(order));
        });
    }

    private WorkOrder findAuthorizedOrderForUpdate(Command command) {
        var orderReference = workOrders.findByCode(command.code())
            .orElseThrow(() -> new NotFoundException(NOT_FOUND_MESSAGE));
        ensureDocumentMatches(orderReference, command.document());

        var order = workOrders.findDetailedByIdForStockUpdate(orderReference.getId())
            .orElseThrow(() -> new NotFoundException(NOT_FOUND_MESSAGE));
        ensureDocumentMatches(order, command.document());
        return order;
    }

    private void ensureDocumentMatches(WorkOrder order, String informedDocument) {
        String document = DocumentValidator.onlyDigits(informedDocument);
        if (!order.getCustomer().getDocumentNumber().equals(document)) {
            throw new NotFoundException(NOT_FOUND_MESSAGE);
        }
    }

    private void saveParts(WorkOrder order) {
        var uniqueParts = new LinkedHashMap<UUID, Part>();
        order.getPartItems().forEach(item -> uniqueParts.put(item.getPart().getId(), item.getPart()));
        uniqueParts.values().forEach(parts::save);
    }
}
