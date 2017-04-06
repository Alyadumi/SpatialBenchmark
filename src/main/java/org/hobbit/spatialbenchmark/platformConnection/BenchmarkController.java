/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.hobbit.spatialbenchmark.platformConnection;

import org.apache.jena.rdf.model.NodeIterator;
import org.hobbit.core.Commands;
import org.hobbit.core.components.AbstractBenchmarkController;
import org.hobbit.spatialbenchmark.platformConnection.util.PlatformConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author jsaveta
 */
public class BenchmarkController extends AbstractBenchmarkController {

    private static final Logger LOGGER = LoggerFactory.getLogger(BenchmarkController.class);
//    private static final String DATA_GENERATOR_CONTAINER_IMAGE = "git.project-hobbit.eu:4567/jsaveta1/spatialdatagenerator";
//    private static final String TASK_GENERATOR_CONTAINER_IMAGE = "git.project-hobbit.eu:4567/jsaveta1/spatialtaskgenerator";
//    private static final String EVALUATION_MODULE_CONTAINER_IMAGE = "git.project-hobbit.eu:4567/jsaveta1/spatialevaluationmodule";

    private static final String DATA_GENERATOR_CONTAINER_IMAGE = "spatial_data-generator";
    private static final String TASK_GENERATOR_CONTAINER_IMAGE = "spatial_task-generator";
    private static final String EVALUATION_MODULE_CONTAINER_IMAGE = "spatial_evaluation-module";

    @Override
    public void init() throws Exception {
        LOGGER.info("PRIN to super.init() tou BenchmarkController ");
        super.init();
        LOGGER.info("META to super.init() tou BenchmarkController ");

        //to data generators den to thelo logika
        int numberOfDataGenerators = (Integer) getProperty("http://w3id.org/bench#hasNumberOfGenerators", 1);
        int population = (Integer) getProperty("http://w3id.org/bench#hasPopulation", 100);
        String serializationFormat = (String) getProperty("http://w3id.org/bench#spatialDataFormat", "n-triples");
        String spatialRelation = (String) getProperty("http://w3id.org/bench#spatialRelation", "COVERS");
        double keepPoints = (double) getProperty("http://w3id.org/bench#keepPoints", 0.3);

        // data generators environmental values
        String[] envVariablesDataGenerator = new String[]{
            PlatformConstants.NUMBER_OF_DATA_GENERATORS + "=" + numberOfDataGenerators,
            PlatformConstants.GENERATED_POPULATION + "=" + population,
            PlatformConstants.GENERATED_DATA_FORMAT + "=" + serializationFormat,
            PlatformConstants.SPATIAL_RELATION + "=" + spatialRelation,
            PlatformConstants.KEEP_POINTS + "=" + keepPoints
        };

        // Create data generators
        createDataGenerators(DATA_GENERATOR_CONTAINER_IMAGE, numberOfDataGenerators, envVariablesDataGenerator);

        LOGGER.info("TELEIWSE TO CREATION TWN DATA GENERATORS");

        // Create task generators
        createTaskGenerators(TASK_GENERATOR_CONTAINER_IMAGE, 1, new String[]{});
        LOGGER.info("TELEIWSE TO CREATION TWN TASK GENERATORS");
        
        // Create evaluation storage
        createEvaluationStorage();
        LOGGER.info("TELEIWSE TO CREATION TOU EVALUATION STORAGE");

        waitForComponentsToInitialize();
        LOGGER.info("TELEIWSE TO INIT TOU BENCHMARK CONTROLLER");
    }

    /**
     * A generic method for loading parameters from the benchmark parameter
     * model
     *
     * @param property the property that we want to load
     * @param defaultValue the default value that will be used in case of an
     * error while loading the property
     */
    @SuppressWarnings("unchecked")
    private <T> T getProperty(String property, T defaultValue) {
        T propertyValue = null;
        NodeIterator iterator = benchmarkParamModel
                .listObjectsOfProperty(benchmarkParamModel
                        .getProperty(property));

        if (iterator.hasNext()) {
            try {
                if (defaultValue instanceof String) {
                    return (T) iterator.next().asLiteral().getString();
                } else if (defaultValue instanceof Integer) {
                    return (T) ((Integer) iterator.next().asLiteral().getInt());
                } else if (defaultValue instanceof Long) {
                    return (T) ((Long) iterator.next().asLiteral().getLong());
                } else if (defaultValue instanceof Double) {
                    return (T) ((Double) iterator.next().asLiteral().getDouble());
                }
            } catch (Exception e) {
                LOGGER.error("Exception while parsing parameter.");
            }
        } else {
            LOGGER.info("Couldn't get property '" + property + "' from the parameter model. Using '" + defaultValue + "' as a default value.");
            propertyValue = defaultValue;
        }
        return propertyValue;
    }

    /*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.hobbit.core.components.AbstractBenchmarkController#executeBenchmark()
     */
    @Override
    protected void executeBenchmark() throws Exception {

        LOGGER.info("MPIKE STO EXECUTE TOU BENCHMARK CONTROLLER");
        // give the start signals
        sendToCmdQueue(Commands.TASK_GENERATOR_START_SIGNAL);
        sendToCmdQueue(Commands.DATA_GENERATOR_START_SIGNAL);
        LOGGER.info("ESTEILE DATA_GENERATOR_START_SIGNAL");
        // wait for the data generators to finish their work
        waitForDataGenToFinish();
        LOGGER.info("TELEIWSE TO DATA GENERATION");

        // wait for the task generators to finish their work
        waitForTaskGenToFinish();
        LOGGER.info("TELEIWSE TO TASK GENERATION");
        // wait for the system to terminate
        waitForSystemToFinish();
        LOGGER.info("TELEIWSE TO SYSTEM");
        // create the evaluation module
//        String[] envVariablesEvaluationModule = new String[] { };
//        createEvaluationModule(EVALUATION_MODULE_CONTAINER_IMAGE, envVariablesEvaluationModule);
//        
//        // wait for the evaluation to finish
//        waitForEvalComponentsToFinish();
//        
        // Send the resultModul to the platform controller and terminate
//        sendResultModel(this.resultModel);
    }
}
