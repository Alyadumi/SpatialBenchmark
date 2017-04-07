/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.hobbit.spatialbenchmark.platformConnection.systems;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import static org.aksw.limes.core.controller.Controller.getConfig;
import static org.aksw.limes.core.controller.Controller.getMapping;
import static org.aksw.limes.core.controller.Controller.parseCommandLine;
import org.aksw.limes.core.controller.ResultMappings;
import org.aksw.limes.core.io.config.Configuration;
import org.aksw.limes.core.io.serializer.ISerializer;
import org.aksw.limes.core.io.serializer.SerializerFactory;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.io.FileUtils;
import org.hobbit.core.components.AbstractSystemAdapter;
import org.hobbit.core.rabbit.RabbitMQUtils;
import org.hobbit.spatialbenchmark.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author jsaveta
 */
public class LimesSystemAdapter extends AbstractSystemAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(LimesSystemAdapter.class);
    private String receivedGeneratedDataFilePath;
    private String dataFormat;
    private String taskFormat;
    private String resultsFile;
    private ResultMappings mappings;

    @Override
    public void init() throws Exception {
        LOGGER.info("Initializing Limes test system...");
        super.init();
        LOGGER.info("Limes initialized successfully .");

    }

    @Override
    public void receiveGeneratedData(byte[] data) {
        LOGGER.info("Starting receiveGeneratedData..");

        ByteBuffer dataBuffer = ByteBuffer.wrap(data);
        // read the file path
        dataFormat = RabbitMQUtils.readString(dataBuffer);
        LOGGER.info("dataFormat " + dataFormat);
        receivedGeneratedDataFilePath = RabbitMQUtils.readString(dataBuffer);
        byte[] receivedGeneratedData = RabbitMQUtils.readByteArray(dataBuffer);
        try {
            FileUtils.writeByteArrayToFile(new File(receivedGeneratedDataFilePath), receivedGeneratedData);
        } catch (IOException ex) {
            java.util.logging.Logger.getLogger(LimesSystemAdapter.class.getName()).log(Level.SEVERE, null, ex);
        }
        // read the file contents
        LOGGER.info("receivedFilePath DATA data " + receivedGeneratedDataFilePath + " !!!");

    }

    @Override
    public void receiveGeneratedTask(String taskId, byte[] data) {
        LOGGER.info("Starting receiveGeneratedTask..");
        try {

            ByteBuffer taskBuffer = ByteBuffer.wrap(data);
            // read the relation
            String taskRelation = RabbitMQUtils.readString(taskBuffer);
            LOGGER.info("taskRelation " + taskRelation);

            // read the file path
            taskFormat = RabbitMQUtils.readString(taskBuffer);
            LOGGER.info("taskFormat " + taskFormat);
            String receivedGeneratedTaskFilePath = RabbitMQUtils.readString(taskBuffer);
            byte[] receivedGeneratedTask = RabbitMQUtils.readByteArray(taskBuffer);
            LOGGER.info("receivedFilePath task " + receivedGeneratedTaskFilePath + " !!!");
            try {
                FileUtils.writeByteArrayToFile(new File(receivedGeneratedTaskFilePath), receivedGeneratedTask);
            } catch (IOException ex) {
                java.util.logging.Logger.getLogger(LimesSystemAdapter.class.getName()).log(Level.SEVERE, null, ex);
            }

            limesController(receivedGeneratedDataFilePath, receivedGeneratedTaskFilePath, taskRelation);

            LOGGER.info("teleiosa to limes controller");

//            BufferedReader br = new BufferedReader(new FileReader(resultsFile));
//            for (String line; (line = br.readLine()) != null;) {
//                LOGGER.info("results 2: " + line);
//            }
            byte[][] resultsArray = new byte[1][];
            resultsArray[0] = FileUtils.readFileToByteArray(new File(resultsFile));
            byte[] results = RabbitMQUtils.writeByteArrays(resultsArray);
            try {
                sendResultToEvalStorage(taskId, results);
                LOGGER.info("Results sent to evaluation storage.");
            } catch (IOException e) {
                LOGGER.error("Exception while sending storage space cost to evaluation storage.", e);
            }
        } catch (IOException ex) {
            java.util.logging.Logger.getLogger(LimesSystemAdapter.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void limesController(String source, String target, String relation) throws IOException {

        LOGGER.info("Started limesController.. ");

//        LOGGER.info("./configs/topologicalConfigs/config" + relation + ".xml");
        String[] args = new String[1];
        args[0] = "./configs/topologicalConfigs/config" + relation + ".xml";

        CommandLine cmd = parseCommandLine(args);
        Configuration config = getConfig(cmd);
        config.getSourceInfo().setEndpoint(source);
        config.getTargetInfo().setEndpoint(target);
        config.getSourceInfo().setType(dataFormat);
        config.getTargetInfo().setType(taskFormat);

        resultsFile = "./datasets/LimesSystemAdapterResults/" + relation + "mappings.nt";

        File dir = new File("./datasets/LimesSystemAdapterResults");
        dir.mkdirs();
        File file = new File(dir, relation + "mappings.nt");
//        FileWriter newJsp = new FileWriter(file);

        config.setAcceptanceFile(resultsFile);
        config.setVerificationFile("./datasets/LimesSystemAdapterResults/" + relation + "absolute_mapping_almost.nt");

//        LOGGER.info("config " + config.toString());
        mappings = getMapping(config);
        writeResults(mappings, config);

        //delete cache folder 
        File folder = new File("./cache/");
        FileUtil.removeDirectory(folder);
        
        LOGGER.info("limesController finished..");
    }

    private static void writeResults(ResultMappings mappings, Configuration config) {
        String outputFormat = config.getOutputFormat();
        ISerializer output = SerializerFactory.createSerializer(outputFormat);
        output.setPrefixes(config.getPrefixes());
        output.writeToFile(mappings.getAcceptanceMapping(), config.getAcceptanceRelation(), config.getAcceptanceFile());
    }

    @Override
    public void close() throws IOException {
        LOGGER.info("Closing System Adapter...");
        // Always close the super class after yours!
        super.close();
        LOGGER.info("System Adapter closed successfully.");

    }
}
