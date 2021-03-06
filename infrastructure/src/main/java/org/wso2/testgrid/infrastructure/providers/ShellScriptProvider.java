/*
 * Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.testgrid.infrastructure.providers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.testgrid.common.InfrastructureProvider;
import org.wso2.testgrid.common.InfrastructureProvisionResult;
import org.wso2.testgrid.common.ShellExecutor;
import org.wso2.testgrid.common.TestPlan;
import org.wso2.testgrid.common.config.InfrastructureConfig;
import org.wso2.testgrid.common.config.Script;
import org.wso2.testgrid.common.exception.CommandExecutionException;
import org.wso2.testgrid.common.exception.TestGridInfrastructureException;
import org.wso2.testgrid.common.util.DataBucketsHelper;
import org.wso2.testgrid.common.util.StringUtil;

import java.nio.file.Paths;

/**
 * This class creates the infrastructure for running tests.
 */
public class ShellScriptProvider implements InfrastructureProvider {

    private static final Logger logger = LoggerFactory.getLogger(ShellScriptProvider.class);
    private static final String SHELL_SCRIPT_PROVIDER = "Shell Executor";

    @Override
    public String getProviderName() {
        return SHELL_SCRIPT_PROVIDER;
    }

    @Override
    public boolean canHandle(InfrastructureConfig infrastructureConfig) {
        return infrastructureConfig.getInfrastructureProvider() == InfrastructureConfig.InfrastructureProvider.SHELL;
    }

    @Override
    public void init(TestPlan testPlan) throws TestGridInfrastructureException {
        String scriptsLocation = testPlan.getScenarioTestsRepository();
        ShellExecutor shellExecutor = new ShellExecutor(Paths.get(scriptsLocation));
        if (testPlan.getScenarioConfig().getScripts() != null
                && testPlan.getScenarioConfig().getScripts().size() > 0) {
            for (Script script : testPlan.getScenarioConfig().getScripts()) {
                if (Script.Phase.CREATE.equals(script.getPhase())) {
                    try {
                        logger.info("Provisioning additional infra");
                        String testInputsLoc = DataBucketsHelper.getInputLocation(testPlan)
                                .toAbsolutePath().toString();
                        final String command = "bash " + script.getFile() + " --input-dir " + testInputsLoc;
                        int exitCode = shellExecutor.executeCommand(command);
                        if (exitCode > 0) {
                            throw new TestGridInfrastructureException(StringUtil.concatStrings(
                                    "Error while executing ", script.getFile(),
                                    ". Script exited with a non-zero exit code (exit code = ", exitCode, ")"));
                        }
                    } catch (CommandExecutionException e) {
                        throw new TestGridInfrastructureException("Error while executing " + script.getFile(), e);
                    }
                    break;
                }
            }
        }
    }

    @Override
    public void cleanup(TestPlan testPlan) throws TestGridInfrastructureException {
        String scriptsLocation = testPlan.getScenarioTestsRepository();
        ShellExecutor shellExecutor = new ShellExecutor(Paths.get(scriptsLocation));
        if (testPlan.getScenarioConfig().getScripts() != null
                && testPlan.getScenarioConfig().getScripts().size() > 0) {
            for (Script script : testPlan.getScenarioConfig().getScripts()) {
                if (Script.Phase.DESTROY.equals(script.getPhase())) {
                    try {
                        String testInputsLoc = DataBucketsHelper.getInputLocation(testPlan)
                                .toAbsolutePath().toString();
                        final String command = "bash " + script.getFile() + " --input-dir " + testInputsLoc;
                        int exitCode = shellExecutor.executeCommand(command);
                        if (exitCode > 0) {
                            throw new TestGridInfrastructureException(StringUtil.concatStrings(
                                    "Error while executing ", script.getFile(),
                                    ". Script exited with a non-zero exit code (exit code = ", exitCode, ")"));
                        }
                    } catch (CommandExecutionException e) {
                        throw new TestGridInfrastructureException("Error while executing " + script.getFile(), e);
                    }
                    break;
                }
            }
        }
    }

    @Override
    public InfrastructureProvisionResult provision(TestPlan testPlan)
            throws TestGridInfrastructureException {
        String testPlanLocation = Paths.get(testPlan.getInfrastructureRepository()).toString();
        InfrastructureConfig infrastructureConfig = testPlan.getInfrastructureConfig();
        logger.info("Executing provisioning scripts...");
        try {
            Script createScript = getScriptToExecute(infrastructureConfig, Script.Phase.CREATE);
            ShellExecutor executor = new ShellExecutor(null);
            InfrastructureProvisionResult result = new InfrastructureProvisionResult();
            String testInputsLoc = DataBucketsHelper.getInputLocation(testPlan)
                    .toAbsolutePath().toString();
            final String command = "bash " + Paths.get(testPlanLocation, createScript.getFile())
                    + " --input-dir " + testInputsLoc;
            int exitCode = executor.executeCommand(command);
            if (exitCode > 0) {
                logger.error(StringUtil.concatStrings("Error occurred while executing the infra-provision script. ",
                        "Script exited with a status code of ", exitCode));
                result.setSuccess(false);
            }
            result.setResultLocation(testPlanLocation);
            return result;

        } catch (CommandExecutionException e) {
            throw new TestGridInfrastructureException(String.format(
                    "Exception occurred while executing the infra-provision script for deployment-pattern '%s'",
                    infrastructureConfig.getProvisioners().get(0).getName()), e);
        }

    }

    @Override
    public boolean release(InfrastructureConfig infrastructureConfig, String infraRepoDir,
                           TestPlan testPlan) throws TestGridInfrastructureException {
        String testPlanLocation = Paths.get(infraRepoDir).toString();

        logger.info("Destroying test environment...");
        ShellExecutor executor = new ShellExecutor(null);
        try {

            String testInputsLoc = DataBucketsHelper.getInputLocation(testPlan)
                    .toAbsolutePath().toString();
            final String command = "bash " + Paths
                    .get(testPlanLocation, getScriptToExecute(infrastructureConfig, Script.Phase.DESTROY)
                            .getFile())
                    + " --input-dir " + testInputsLoc;
            int exitCode = executor.executeCommand(command);
            return exitCode == 0;
        } catch (CommandExecutionException e) {
            throw new TestGridInfrastructureException(
                    "Exception occurred while executing the infra-destroy script " + "for deployment-pattern '"
                            + infrastructureConfig.getProvisioners().get(0).getName() + "'", e);
        }
    }

    /**
     * This method returns the script matching the correct script phase.
     *
     * @param infrastructureConfig {@link InfrastructureConfig} object with current infrastructure configurations
     * @param scriptPhase          {@link Script.Phase} enum value for required script
     * @return the matching script from deployment configuration
     * @throws TestGridInfrastructureException if there is no matching script for phase defined
     */
    private Script getScriptToExecute(InfrastructureConfig infrastructureConfig, Script.Phase scriptPhase)
            throws TestGridInfrastructureException {

        for (Script script : infrastructureConfig.getProvisioners().get(0).getScripts()) {
            if (scriptPhase.equals(script.getPhase())) {
                return script;
            }
        }
        if (Script.Phase.CREATE.equals(scriptPhase)) {
            for (Script script : infrastructureConfig.getProvisioners().get(0).getScripts()) {
                if (script.getPhase() == null) {
                    return script;
                }
            }
        }
        throw new TestGridInfrastructureException("The infrastructure provisioner's script list doesn't "
                + "contain the script for '" + scriptPhase.toString() + "' phase");
    }
}
