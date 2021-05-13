package com.example.droolsclientpoc.controllers;

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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@RestController
public class ApiController
{
    private static final String URL = "http://docker:8180/kie-server/services/rest/server";
    private static final String USER = "kieserver";
    private static final String PASSWORD = "kieserver1!";
    
    private KieServicesClient kieServicesClient;

    @PostConstruct
    private void initialize()
    {
        KieServicesConfiguration conf = KieServicesFactory.newRestConfiguration(URL, USER, PASSWORD);
        conf.setMarshallingFormat(MarshallingFormat.JSON);

        kieServicesClient = KieServicesFactory.newKieServicesClient(conf);
    }

    @GetMapping("/rules")
    public ResponseEntity<String> rules()
    {

        List<KieContainerResource> kieContainers = kieServicesClient.listContainers().getResult().getContainers();
        if (kieContainers.isEmpty())
        {
            System.out.println("No containers available...");
            return ResponseEntity.notFound().build();
        }

        for (KieContainerResource kr : kieContainers)
            System.out.printf("Container id: %s%n", kr.getContainerId());

        final String containerId = kieContainers.get(0).getContainerId();

        RuleServicesClient rulesClient = kieServicesClient.getServicesClient(RuleServicesClient.class);
        KieCommands commandsFactory = KieServices.Factory.get().getCommands();

        final String obj = "test";
        final String uuid = UUID.randomUUID().toString();

        Command<?> insert = commandsFactory.newInsert(obj, uuid);
        Command<?> fireAllRules = commandsFactory.newFireAllRules();
        Command<?> batchCommand = commandsFactory.newBatchExecution(Arrays.asList(insert, fireAllRules));

        ServiceResponse<ExecutionResults> executeResponse = rulesClient.executeCommandsWithResults(containerId, batchCommand);

        if (executeResponse.getType() != KieServiceResponse.ResponseType.SUCCESS)
        {
            System.out.println("Error executing rules. Message: ");
            System.out.println(executeResponse.getMsg());

            return ResponseEntity.badRequest().build();
        }

        System.out.println("Commands executed with success! Response: ");
        System.out.println(executeResponse.getResult().toString());

        FactHandle factHandle = (FactHandle) executeResponse.getResult().getFactHandle(uuid);
        Object value = executeResponse.getResult().getValue(uuid);

        return deleteFactFromSession(containerId, factHandle)
                ? ResponseEntity.ok("ok")
                : ResponseEntity.badRequest().build();

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
        {
            return true;
        }

        System.err.println("Error deleting fact: " + deleteResult.getMsg());
        return false;
    }
}
