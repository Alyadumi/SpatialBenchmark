package org.hobbit.spatialbenchmark.platformConnection;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.SerializationUtils;
import org.hobbit.core.components.AbstractDataGenerator;
import org.hobbit.core.mimic.DockerBasedMimickingAlg;
import org.hobbit.core.rabbit.RabbitMQUtils;
import org.hobbit.core.rabbit.SimpleFileSender;
import org.hobbit.spatialbenchmark.data.Generator;
import static org.hobbit.spatialbenchmark.data.Generator.getConfigurations;
import static org.hobbit.spatialbenchmark.data.Generator.getDefinitions;
import static org.hobbit.spatialbenchmark.data.Generator.getRandom;
import static org.hobbit.spatialbenchmark.data.Generator.getRelationsCall;
import static org.hobbit.spatialbenchmark.data.Generator.setSpatialTransformation;
import org.hobbit.spatialbenchmark.data.Worker;
import org.hobbit.spatialbenchmark.platformConnection.util.PlatformConstants;
import org.hobbit.spatialbenchmark.properties.Configurations;
import org.hobbit.spatialbenchmark.properties.Definitions;
import org.hobbit.spatialbenchmark.util.AllocationsUtil;
import org.hobbit.spatialbenchmark.transformations.RelationsCall.spatialRelation;

/**
 *
 * @author jsaveta
 */
public class DataGenerator extends AbstractDataGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(DataGenerator.class);

    private int numberOfDataGenerators; //TODO: use this
    private int population;
    private int seed;
    public static String serializationFormat;
    private String relation;
    private double keepPoints;
    private int taskId = 0;

    public static Generator dataGeneration = new Generator();

    public static String testPropertiesFile = System.getProperty("user.dir") + File.separator + "test.properties";
    public static String definitionsPropertiesFile = System.getProperty("user.dir") + File.separator + "definitions.properties";
    public static String datasetsPath = System.getProperty("user.dir") + File.separator + "datasets";
    public static String givenDatasetsPath = System.getProperty("user.dir") + File.separator + "datasets" + File.separator + "givenDatasets";

    private Task task;

    @Override
    public void init() throws Exception {
        LOGGER.info("Initializing Data Generator '" + getGeneratorId() + "'");
        super.init();

        // Initialize data generation parameters through the environment variables given by user
        initFromEnv();

        // Given the above input, update configuration files that are necessary for data generation
        reInitializeProperties();

        // call mimicking algorithm
        runMimicking();

        task = new Task(Integer.toString(taskId++), null, null, null);

    }

    @Override
    protected void generateData() throws Exception {
        // Create your data inside this method. You might want to use the
        // id of this data generator and the number of all data generators
        // running in parallel.

//        int dataGeneratorId = getGeneratorId();
//        int numberOfGenerators = getNumberOfGenerators();
        LOGGER.info("Generate data.. ");
        SimpleFileSender sender = null;
        try {

            Worker worker = new Worker();
            worker.execute();

            File sourcePath = new File(getConfigurations().getString(Configurations.DATASETS_PATH) + File.separator + "SourceDatasets");
            ArrayList<File> sourceFiles = new ArrayList<File>(Arrays.asList(sourcePath.listFiles()));

            File targetPath = new File(getConfigurations().getString(Configurations.DATASETS_PATH) + File.separator + "TargetDatasets");
            ArrayList<File> targetFiles = new ArrayList<File>(Arrays.asList(targetPath.listFiles()));

            File gsPath = new File(getConfigurations().getString(Configurations.DATASETS_PATH) + File.separator + "GoldStandards");
            ArrayList<File> gsFiles = new ArrayList<File>(Arrays.asList(gsPath.listFiles()));

            for (File file : gsFiles) {
                byte[][] generatedFileArray = new byte[3][];
                // send the file name and its content
                generatedFileArray[0] = RabbitMQUtils.writeString(serializationFormat);
                generatedFileArray[1] = RabbitMQUtils.writeString(file.getAbsolutePath());
                LOGGER.info("file.getAbsolutePath() gs " + file.getAbsolutePath());
                generatedFileArray[2] = FileUtils.readFileToByteArray(file);
                // convert them to byte[]
                byte[] generatedFile = RabbitMQUtils.writeByteArrays(generatedFileArray);

                task.setExpectedAnswers(generatedFile);
                LOGGER.info("Gold Standard successfully added to Task.");
            }

            // send generated data to system adapter
            for (File file : sourceFiles) {
                byte[][] generatedFileArray = new byte[2][];
                // send the file name and its content
                generatedFileArray[0] = RabbitMQUtils.writeString(serializationFormat);
                generatedFileArray[1] = RabbitMQUtils.writeString(file.getAbsolutePath());
                // convert them to byte[]
                byte[] generatedFile = RabbitMQUtils.writeByteArrays(generatedFileArray);

                // define a queue name, e.g., read it from the environment
                String queueName = "source_file";

                // create the sender
                sender = SimpleFileSender.create(this.outgoingDataQueuefactory, queueName);

                InputStream is = null;
                try {
                    // create input stream, e.g., by opening a file
                    is = new FileInputStream(file);
                    // send data
                    sender.streamData(is, file.getName());
                } catch (Exception e) {
                    // handle exception
                } finally {
                    IOUtils.closeQuietly(is);
                }

                // close the sender
                IOUtils.closeQuietly(sender);
                // send data to system
                sendDataToSystemAdapter(generatedFile);
                LOGGER.info(file.getAbsolutePath() + " (" + (double) file.length() / 1000 + " KB) sent to System Adapter.");

            }

            // send generated tasks along with their expected answers to task generator
            for (File file : targetFiles) {
                byte[][] generatedFileArray = new byte[2][];
                // send the file name and its content
                generatedFileArray[0] = RabbitMQUtils.writeString(serializationFormat);
                generatedFileArray[1] = RabbitMQUtils.writeString(file.getAbsolutePath());
                // convert them to byte[]
                byte[] generatedFile = RabbitMQUtils.writeByteArrays(generatedFileArray);
                task.setTarget(generatedFile);
                task.setRelation(relation);

                byte[] data = SerializationUtils.serialize(task);

                // define a queue name, e.g., read it from the environment
                String queueName = "target_file";
                // create the sender
                sender = SimpleFileSender.create(this.outgoingDataQueuefactory, queueName);

                InputStream is = null;
                try {
                    // create input stream, e.g., by opening a file
                    is = new FileInputStream(file);
                    // send data
                    sender.streamData(is, file.getName());
                } catch (Exception e) {
                    // handle exception
                } finally {
                    IOUtils.closeQuietly(is);
                }

                // close the sender
                IOUtils.closeQuietly(sender);

                sendDataToTaskGenerator(data);
                LOGGER.info("Target data successfully sent to Task Generator.");
            }

        } catch (Exception e) {
            LOGGER.error("Exception while sending file to System Adapter or Task Generator(s).", e);
        }

    }

    public void initFromEnv() {
        LOGGER.info("Getting Data Generator's properites from the environment...");

        Map<String, String> env = System.getenv();
        serializationFormat = (String) getFromEnv(env, PlatformConstants.GENERATED_DATA_FORMAT, "");
        population = (Integer) getFromEnv(env, PlatformConstants.GENERATED_POPULATION, 0);
        seed = (Integer) getFromEnv(env, PlatformConstants.GENERATED_TOMTOM_SEED, 0);
        numberOfDataGenerators = (Integer) getFromEnv(env, PlatformConstants.NUMBER_OF_DATA_GENERATORS, 0);
        relation = (String) getFromEnv(env, PlatformConstants.SPATIAL_RELATION, "");
        keepPoints = (double) getFromEnv(env, PlatformConstants.KEEP_POINTS, 0.0);
    }

    /**
     * A generic method for initialize benchmark parameters from environment
     * variables
     *
     * @param env a map of all available environment variables
     * @param parameter the property that we want to get
     * @param paramType a dummy parameter to recognize property's type
     */
    @SuppressWarnings("unchecked")
    private <T> T getFromEnv(Map<String, String> env, String parameter, T paramType) {
        if (!env.containsKey(parameter)) {
            LOGGER.error(
                    "Environment variable \"" + parameter + "\" is not set. Aborting.");
            throw new IllegalArgumentException(
                    "Environment variable \"" + parameter + "\" is not set. Aborting.");
        }
        try {
            if (paramType instanceof String) {
                return (T) env.get(parameter);
            } else if (paramType instanceof Integer) {
                return (T) (Integer) Integer.parseInt(env.get(parameter));
            } else if (paramType instanceof Long) {
                return (T) (Long) Long.parseLong(env.get(parameter));
            } else if (paramType instanceof Double) {
                return (T) (Double) Double.parseDouble(env.get(parameter));
            }
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Couldn't get \"" + parameter + "\" from the environment. Aborting.", e);
        }
        return paramType;
    }

    public void reInitializeProperties() throws IOException {

//        int numberOfGenerators = getNumberOfGenerators();
//        int generatorId = getGeneratorId();
        loadPropertiesConfigurationFiles();

        getDefinitions().initializeAllocations(getRandom());

        // re-initialize test.properties file that is required for data generation
        getConfigurations().setStringProperty(Configurations.INSTANCES, String.valueOf(population));
        getConfigurations().setStringProperty(Configurations.GENERATED_DATA_FORMAT, serializationFormat);
        getConfigurations().setStringProperty(Configurations.DATASETS_PATH, datasetsPath);
        getConfigurations().setStringProperty(Configurations.GIVEN_DATASETS_PATH, givenDatasetsPath);

        //TODO : check if keep points < 1.0
        ArrayList<Double> points = new ArrayList<Double>();
        points.add(keepPoints);
        points.add(1.0 - keepPoints);
        Random random = new Random();
        Definitions.keepPointsAllocation = new AllocationsUtil(points, random);

        ArrayList<Double> relationArrayList = new ArrayList<Double>();
        for (int i = 0; i < 10; i++) {
            relationArrayList.add(0.0);
        }

        int index = spatialRelation.valueOf(relation).ordinal();
        relationArrayList.add(index, 1.0);
        Definitions.spatialRelationsAllocation = new AllocationsUtil(relationArrayList, random);

        getRelationsCall().spatialRelationsCases();
        setSpatialTransformation(getRelationsCall().getSpatialRelationsConfiguration());

    }

    public static void loadPropertiesConfigurationFiles() throws IOException {
        getConfigurations().loadFromFile(testPropertiesFile);
        getDefinitions().loadFromFile(definitionsPropertiesFile);
        //getDefinitions().loadFromFile(configurations.getString(Configurations.DEFINITIONS_PATH));
    }

    /**
     * Initializes and runs a mimicking algorithm.
     *
     */
    public void runMimicking() {
        LOGGER.info("Running mimicking algorithm ");
//git.project-hobbit.eu:4567/filipe.teixeira/synthetic-trace-generator
        DockerBasedMimickingAlg alg = new DockerBasedMimickingAlg(this, "git.project-hobbit.eu:4567/filipe.teixeira/synthetic-trace-generator");

        try {

            /*"hobbit.numtraces": number of traces to generate
            "hobbit.seed": seed for the random generator
            "hobbit.outputformat": format for the output (supports "kml", "csv" and "rdf")
             */
            ///* Seed for the mimicking algorithm */
//    private String DATA_GENERATOR_SEED = null;
//vale sta parameter sto ttl kai ston kodika to seed tou mimicking algortihm.. rota ti akrivos einai!?
            String[] TomTomDataArguments = new String[3];
            TomTomDataArguments[0] = "hobbit.numtraces=" + population;
            TomTomDataArguments[1] = "hobbit.seed=" + seed;
            TomTomDataArguments[2] = "hobbit.outputformat=rdf";

            alg.generateData(givenDatasetsPath, TomTomDataArguments);
            //print files in folder
            File[] files = new File(givenDatasetsPath).listFiles();
//If this pathname does not denote a directory, then listFiles() returns null. 
            LOGGER.info("files generated from mimicking: " + files.length);
            for (File file : files) {
                if (file.isFile()) {
                    LOGGER.info("file from mimicking: " + file.getName());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            LOGGER.error("TOMTOM_DATA script terminated.");
            throw new RuntimeException();
        }

        LOGGER.info("Mimicking data has been received.");

    }

    @Override
    public void close() throws IOException {
        LOGGER.info("Closing Data Generator...");
        super.close();
        LOGGER.info("Data Generator closed successfully.");
    }

}
