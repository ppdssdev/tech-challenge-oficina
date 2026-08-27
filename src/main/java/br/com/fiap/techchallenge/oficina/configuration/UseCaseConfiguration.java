package br.com.fiap.techchallenge.oficina.configuration;

import br.com.fiap.techchallenge.oficina.application.port.in.AddWorkOrderItemsUseCase;
import br.com.fiap.techchallenge.oficina.application.port.in.AuthenticateUserUseCase;
import br.com.fiap.techchallenge.oficina.application.port.in.CalculateWorkOrderMetricsUseCase;
import br.com.fiap.techchallenge.oficina.application.port.in.CreateWorkOrderUseCase;
import br.com.fiap.techchallenge.oficina.application.port.in.DecideBudgetUseCase;
import br.com.fiap.techchallenge.oficina.application.port.in.DeliverWorkOrderUseCase;
import br.com.fiap.techchallenge.oficina.application.port.in.FinishWorkOrderUseCase;
import br.com.fiap.techchallenge.oficina.application.port.in.GetWorkOrderUseCase;
import br.com.fiap.techchallenge.oficina.application.port.in.ListWorkOrdersUseCase;
import br.com.fiap.techchallenge.oficina.application.port.in.ManageCustomersUseCase;
import br.com.fiap.techchallenge.oficina.application.port.in.ManagePartsUseCase;
import br.com.fiap.techchallenge.oficina.application.port.in.ManageServiceCatalogUseCase;
import br.com.fiap.techchallenge.oficina.application.port.in.ManageVehiclesUseCase;
import br.com.fiap.techchallenge.oficina.application.port.in.StartDiagnosisUseCase;
import br.com.fiap.techchallenge.oficina.application.port.in.UpdateDiagnosisUseCase;
import br.com.fiap.techchallenge.oficina.application.port.out.CredentialVerifierPort;
import br.com.fiap.techchallenge.oficina.application.port.out.CustomerRepositoryPort;
import br.com.fiap.techchallenge.oficina.application.port.out.PartRepositoryPort;
import br.com.fiap.techchallenge.oficina.application.port.out.ServiceCatalogRepositoryPort;
import br.com.fiap.techchallenge.oficina.application.port.out.TokenIssuerPort;
import br.com.fiap.techchallenge.oficina.application.port.out.TransactionPort;
import br.com.fiap.techchallenge.oficina.application.port.out.VehicleRepositoryPort;
import br.com.fiap.techchallenge.oficina.application.port.out.WorkOrderRepositoryPort;
import br.com.fiap.techchallenge.oficina.application.usecase.AddWorkOrderItemsService;
import br.com.fiap.techchallenge.oficina.application.usecase.AuthenticateUserService;
import br.com.fiap.techchallenge.oficina.application.usecase.CalculateWorkOrderMetricsService;
import br.com.fiap.techchallenge.oficina.application.usecase.CreateWorkOrderService;
import br.com.fiap.techchallenge.oficina.application.usecase.DecideBudgetService;
import br.com.fiap.techchallenge.oficina.application.usecase.DeliverWorkOrderService;
import br.com.fiap.techchallenge.oficina.application.usecase.FinishWorkOrderService;
import br.com.fiap.techchallenge.oficina.application.usecase.GetWorkOrderService;
import br.com.fiap.techchallenge.oficina.application.usecase.ListWorkOrdersService;
import br.com.fiap.techchallenge.oficina.application.usecase.ManageCustomersService;
import br.com.fiap.techchallenge.oficina.application.usecase.ManagePartsService;
import br.com.fiap.techchallenge.oficina.application.usecase.ManageServiceCatalogService;
import br.com.fiap.techchallenge.oficina.application.usecase.ManageVehiclesService;
import br.com.fiap.techchallenge.oficina.application.usecase.StartDiagnosisService;
import br.com.fiap.techchallenge.oficina.application.usecase.UpdateDiagnosisService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UseCaseConfiguration {
    @Bean ManageCustomersUseCase customers(CustomerRepositoryPort repo, TransactionPort tx) {
        return new ManageCustomersService(repo, tx);
    }

    @Bean ManagePartsUseCase parts(PartRepositoryPort repo, TransactionPort tx) {
        return new ManagePartsService(repo, tx);
    }

    @Bean ManageServiceCatalogUseCase serviceCatalog(ServiceCatalogRepositoryPort repo, TransactionPort tx) {
        return new ManageServiceCatalogService(repo, tx);
    }

    @Bean ManageVehiclesUseCase vehicles(VehicleRepositoryPort vehicles, CustomerRepositoryPort customers, TransactionPort tx) {
        return new ManageVehiclesService(vehicles, customers, tx);
    }

    @Bean CreateWorkOrderUseCase createWorkOrder(
        WorkOrderRepositoryPort orders, CustomerRepositoryPort customers, VehicleRepositoryPort vehicles,
        ServiceCatalogRepositoryPort services, PartRepositoryPort parts, TransactionPort tx
    ) {
        return new CreateWorkOrderService(orders, customers, vehicles, services, parts, tx);
    }

    @Bean GetWorkOrderUseCase getWorkOrder(WorkOrderRepositoryPort orders, TransactionPort tx) {
        return new GetWorkOrderService(orders, tx);
    }

    @Bean ListWorkOrdersUseCase listWorkOrders(WorkOrderRepositoryPort orders, TransactionPort tx) {
        return new ListWorkOrdersService(orders, tx);
    }

    @Bean StartDiagnosisUseCase startDiagnosis(WorkOrderRepositoryPort orders, TransactionPort tx) {
        return new StartDiagnosisService(orders, tx);
    }

    @Bean UpdateDiagnosisUseCase updateDiagnosis(WorkOrderRepositoryPort orders, TransactionPort tx) {
        return new UpdateDiagnosisService(orders, tx);
    }

    @Bean AddWorkOrderItemsUseCase addWorkOrderItems(
        WorkOrderRepositoryPort orders, ServiceCatalogRepositoryPort services, PartRepositoryPort parts, TransactionPort tx
    ) {
        return new AddWorkOrderItemsService(orders, services, parts, tx);
    }

    @Bean DecideBudgetUseCase decideBudget(WorkOrderRepositoryPort orders, PartRepositoryPort parts, TransactionPort tx) {
        return new DecideBudgetService(orders, parts, tx);
    }

    @Bean FinishWorkOrderUseCase finishWorkOrder(WorkOrderRepositoryPort orders, TransactionPort tx) {
        return new FinishWorkOrderService(orders, tx);
    }

    @Bean DeliverWorkOrderUseCase deliverWorkOrder(WorkOrderRepositoryPort orders, TransactionPort tx) {
        return new DeliverWorkOrderService(orders, tx);
    }

    @Bean CalculateWorkOrderMetricsUseCase workOrderMetrics(WorkOrderRepositoryPort orders, TransactionPort tx) {
        return new CalculateWorkOrderMetricsService(orders, tx);
    }

    @Bean AuthenticateUserUseCase authenticateUser(CredentialVerifierPort credentials, TokenIssuerPort tokens) {
        return new AuthenticateUserService(credentials, tokens);
    }
}
