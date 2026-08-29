package br.com.fiap.techchallenge.oficina.architecture;

import br.com.fiap.techchallenge.oficina.adapters.in.web.dto.workorder.AddWorkOrderItemsRequest;
import br.com.fiap.techchallenge.oficina.adapters.in.web.dto.workorder.BudgetDecisionRequest;
import br.com.fiap.techchallenge.oficina.adapters.in.web.dto.workorder.RequestedPartInput;
import br.com.fiap.techchallenge.oficina.adapters.in.web.dto.workorder.RequestedServiceInput;
import br.com.fiap.techchallenge.oficina.application.port.in.AddWorkOrderItemsUseCase;
import br.com.fiap.techchallenge.oficina.application.port.in.AuthenticateUserUseCase;
import br.com.fiap.techchallenge.oficina.application.port.in.CalculateWorkOrderMetricsUseCase;
import br.com.fiap.techchallenge.oficina.application.port.in.CreateWorkOrderUseCase;
import br.com.fiap.techchallenge.oficina.application.port.in.DecideBudgetUseCase;
import br.com.fiap.techchallenge.oficina.application.port.in.DeliverWorkOrderUseCase;
import br.com.fiap.techchallenge.oficina.application.port.in.FinishWorkOrderUseCase;
import br.com.fiap.techchallenge.oficina.application.port.in.ExternalBudgetDecisionUseCase;
import br.com.fiap.techchallenge.oficina.application.port.in.GetWorkOrderUseCase;
import br.com.fiap.techchallenge.oficina.application.port.in.ListWorkOrdersUseCase;
import br.com.fiap.techchallenge.oficina.application.port.in.ManageCustomersUseCase;
import br.com.fiap.techchallenge.oficina.application.port.in.ManagePartsUseCase;
import br.com.fiap.techchallenge.oficina.application.port.in.ManageServiceCatalogUseCase;
import br.com.fiap.techchallenge.oficina.application.port.in.ManageVehiclesUseCase;
import br.com.fiap.techchallenge.oficina.application.port.in.NotifyBudgetUseCase;
import br.com.fiap.techchallenge.oficina.application.port.in.StartDiagnosisUseCase;
import br.com.fiap.techchallenge.oficina.application.port.in.UpdateDiagnosisUseCase;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationBoundaryTest {

    private static final String DOMAIN_MODEL_PACKAGE =
        "br.com.fiap.techchallenge.oficina.domain.model";

    @Test
    void inputPortsShouldNotReturnDomainModels() {
        inputPorts().stream()
            .flatMap(port -> Arrays.stream(port.getDeclaredMethods()))
            .forEach(method -> assertThat(referencesDomainModel(method.getGenericReturnType()))
                .as("retorno de %s.%s", method.getDeclaringClass().getSimpleName(), method.getName())
                .isFalse());
    }

    @Test
    void httpItemRequestsShouldNotImplementApplicationCommands() {
        assertThat(AddWorkOrderItemsRequest.class.getInterfaces()).isEmpty();
        assertThat(BudgetDecisionRequest.class.getInterfaces()).isEmpty();
        assertThat(RequestedServiceInput.class.getInterfaces()).isEmpty();
        assertThat(RequestedPartInput.class.getInterfaces()).isEmpty();
    }

    private static List<Class<?>> inputPorts() {
        return List.of(
            AddWorkOrderItemsUseCase.class,
            AuthenticateUserUseCase.class,
            CalculateWorkOrderMetricsUseCase.class,
            CreateWorkOrderUseCase.class,
            DecideBudgetUseCase.class,
            DeliverWorkOrderUseCase.class,
            ExternalBudgetDecisionUseCase.class,
            FinishWorkOrderUseCase.class,
            GetWorkOrderUseCase.class,
            ListWorkOrdersUseCase.class,
            ManageCustomersUseCase.class,
            ManagePartsUseCase.class,
            ManageServiceCatalogUseCase.class,
            ManageVehiclesUseCase.class,
            NotifyBudgetUseCase.class,
            StartDiagnosisUseCase.class,
            UpdateDiagnosisUseCase.class
        );
    }

    private static boolean referencesDomainModel(Type type) {
        if (type instanceof Class<?> typeClass) {
            return typeClass.getPackageName().startsWith(DOMAIN_MODEL_PACKAGE);
        }
        if (type instanceof ParameterizedType parameterizedType) {
            return referencesDomainModel(parameterizedType.getRawType())
                || Arrays.stream(parameterizedType.getActualTypeArguments())
                    .anyMatch(ApplicationBoundaryTest::referencesDomainModel);
        }
        return false;
    }
}
