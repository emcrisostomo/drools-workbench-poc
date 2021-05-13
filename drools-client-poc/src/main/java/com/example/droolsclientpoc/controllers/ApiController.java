package com.example.droolsclientpoc.controllers;

import com.example.droolsclientpoc.dtos.Permiso;
import org.kie.api.KieServices;
import org.kie.api.command.BatchExecutionCommand;
import org.kie.api.command.Command;
import org.kie.api.command.KieCommands;
import org.kie.api.runtime.ExecutionResults;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.server.api.marshalling.MarshallingFormat;
import org.kie.server.api.model.KieContainerResource;
import org.kie.server.api.model.KieServiceResponse;
import org.kie.server.api.model.ServiceResponse;
import org.kie.server.client.KieServicesClient;
import org.kie.server.client.KieServicesConfiguration;
import org.kie.server.client.KieServicesFactory;
import org.kie.server.client.RuleServicesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PostConstruct;
import java.util.*;

@RestController
public class ApiController
{
    private static final String URL = "http://docker:8180/kie-server/services/rest/server";
    private static final String USER = "kieserver";
    private static final String PASSWORD = "kieserver1!";
    private static final Logger logger = LoggerFactory.getLogger(ApiController.class);

    private KieServicesClient kieServicesClient;
    private boolean usePermissionClass;

    @PostConstruct
    private void initialize()
    {
        KieServicesConfiguration conf = KieServicesFactory.newRestConfiguration(URL, USER, PASSWORD);
        conf.setMarshallingFormat(MarshallingFormat.JSON);

        if (usePermissionClass)
        {
            Set<Class<?>> extraClasses = new HashSet<>();
            extraClasses.add(Permiso.class);
            conf.addExtraClasses(extraClasses);
        }

        kieServicesClient = KieServicesFactory.newKieServicesClient(conf);
    }

    @GetMapping("/rules")
    public ResponseEntity<String> rules()
    {
        final Optional<String> containerId = getFirstContainerId();

        if (containerId.isEmpty())
            return ResponseEntity.notFound().build();

        RuleServicesClient rulesClient = kieServicesClient.getServicesClient(RuleServicesClient.class);
        KieCommands commandsFactory = KieServices.Factory.get().getCommands();

        final Object fact = buildFact();
        final var uuid = UUID.randomUUID().toString();

        Command<?> insert = commandsFactory.newInsert(fact, uuid);
        Command<?> fireAllRules = commandsFactory.newFireAllRules();
        Command<?> batchCommand = commandsFactory.newBatchExecution(Arrays.asList(insert, fireAllRules));

        ServiceResponse<ExecutionResults> executeResponse = rulesClient.executeCommandsWithResults(containerId.get(), batchCommand);

        if (executeResponse.getType() != KieServiceResponse.ResponseType.SUCCESS)
        {
            logger.error("Error executing rules. Message: {}", executeResponse.getMsg());
            return ResponseEntity.badRequest().build();
        }

        logger.info("Commands executed with success! Response: {}", executeResponse.getResult());

        final var factHandle = (FactHandle) executeResponse.getResult().getFactHandle(uuid);
        final Object value = executeResponse.getResult().getValue(uuid);

        logger.debug("Fact retrieved from handle: {}", value);

        return deleteFactFromSession(containerId.get(), factHandle)
                ? ResponseEntity.ok("ok")
                : ResponseEntity.badRequest().build();
    }

    private Optional<String> getFirstContainerId()
    {
        final List<KieContainerResource> kieContainers = kieServicesClient.listContainers().getResult().getContainers();
        if (kieContainers.isEmpty())
        {
            logger.error("No containers available...");
            return Optional.empty();
        }

        for (KieContainerResource kr : kieContainers)
            logger.info("Container id: {}", kr.getContainerId());

        return Optional.ofNullable(kieContainers.get(0).getContainerId());
    }

    private Object buildFact()
    {
        return usePermissionClass ? new Permiso(1) : "staticFact";
    }

    private boolean deleteFactFromSession(String containerId, FactHandle factHandle)
    {
        final KieCommands commandsFactory = KieServices.Factory.get().getCommands();
        final RuleServicesClient rulesClient = kieServicesClient.getServicesClient(RuleServicesClient.class);

        List<Command<?>> deleteCommands = new ArrayList<>();
        deleteCommands.add(commandsFactory.newDelete(factHandle));

        BatchExecutionCommand deleteBatch = commandsFactory.newBatchExecution(deleteCommands);
        ServiceResponse<ExecutionResults> deleteResult = rulesClient.executeCommandsWithResults(containerId, deleteBatch);

        if (deleteResult.getType() == KieServiceResponse.ResponseType.SUCCESS)
            return true;

        logger.error("Error deleting fact: {}", deleteResult.getMsg());
        return false;
    }
}
