package br.com.fiap.techchallenge.oficina.api;

import br.com.fiap.techchallenge.oficina.adapters.in.web.*;
import br.com.fiap.techchallenge.oficina.application.port.in.*;
import br.com.fiap.techchallenge.oficina.domain.exception.NotFoundException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PublicStatusContractTest {
    @ParameterizedTest
    @ValueSource(strings = {"local", "prod", "prod,local"})
    void registersLegacyOnlyLocallyAndSupportsJsonPost(String profile) {
        var service = mock(GetWorkOrderUseCase.class);
        when(service.getPublicStatus("OS-X", "52998224725")).thenThrow(new NotFoundException("Não encontrada"));
        new ApplicationContextRunner()
            .withPropertyValues("spring.profiles.active=" + profile)
            .withBean(GetWorkOrderUseCase.class, () -> service)
            .withBean(ExternalBudgetDecisionUseCase.class, () -> mock(ExternalBudgetDecisionUseCase.class))
            .withUserConfiguration(PublicWorkOrderController.class, LegacyPublicWorkOrderController.class)
            .run(context -> {
                var builder = MockMvcBuilders.standaloneSetup(context.getBean(PublicWorkOrderController.class));
                if (profile.equals("local")) {
                    assertThat(context).hasSingleBean(LegacyPublicWorkOrderController.class);
                    builder = MockMvcBuilders.standaloneSetup(context.getBean(PublicWorkOrderController.class),
                        context.getBean(LegacyPublicWorkOrderController.class));
                } else {
                    assertThat(context).doesNotHaveBean(LegacyPublicWorkOrderController.class);
                }
                var mvc = builder.setControllerAdvice(new ApiExceptionHandler()).build();
                mvc.perform(get("/api/v1/public/work-orders/OS-X/status").param("document", "52998224725"))
                    .andExpect(profile.equals("local") ? status().isNotFound() : status().isMethodNotAllowed());
                mvc.perform(post("/api/v1/public/work-orders/OS-X/status")
                    .contentType("application/json").content("{\"document\":\"52998224725\"}"))
                    .andExpect(status().isNotFound());
                mvc.perform(post("/api/v1/public/work-orders/OS-X/status")
                    .contentType("application/json").content("{}")).andExpect(status().isBadRequest());
                verify(service, times(profile.equals("local") ? 2 : 1)).getPublicStatus("OS-X", "52998224725");
            });
    }
}
