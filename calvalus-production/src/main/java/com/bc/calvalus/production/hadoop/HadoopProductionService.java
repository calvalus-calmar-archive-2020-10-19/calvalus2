package com.bc.calvalus.production.hadoop;


import com.bc.calvalus.catalogue.ProductSet;
import com.bc.calvalus.processing.beam.BeamJobService;
import com.bc.calvalus.processing.beam.StreamingProductReader;
import com.bc.calvalus.production.Production;
import com.bc.calvalus.production.ProductionException;
import com.bc.calvalus.production.ProductionProcessor;
import com.bc.calvalus.production.ProductionRequest;
import com.bc.calvalus.production.ProductionResponse;
import com.bc.calvalus.production.ProductionService;
import com.bc.calvalus.production.ProductionState;
import com.bc.calvalus.production.ProductionStatus;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.PathFilter;
import org.apache.hadoop.mapred.JobClient;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.JobStatus;
import org.apache.hadoop.mapred.RunningJob;
import org.apache.hadoop.mapreduce.JobID;
import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.datamodel.Product;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A ProductionService implementation that delegates to a Hadoop cluster.
 * To use it, specify the servlet init-parameter 'calvalus.portal.backendService.class'
 * (context.xml or web.xml)
 */
public class HadoopProductionService implements ProductionService {

    public static final File PRODUCTIONS_DB_FILE = new File("calvalus-productions-db.csv");
    private static final int HADOOP_OBSERVATION_PERIOD = 2000;
    private final JobClient jobClient;
    private final HadoopProductionDatabase database;
    private final StagingService stagingService;
    // todo - Persist
    private final Logger logger;
    private final File stagingDirectory;
    private WpsXmlGenerator wpsXmlGenerator;

    public HadoopProductionService(JobConf jobConf, Logger logger, File stagingDirectory) throws ProductionException {
        this.logger = logger;
        this.stagingDirectory = stagingDirectory;
        try {
            this.jobClient = new JobClient(jobConf);
        } catch (IOException e) {
            throw new ProductionException("Failed to create Hadoop JobClient." + e.getMessage(), e);
        }
        this.database = new HadoopProductionDatabase(new HadoopL3ProcessingRequestFactory(jobClient));

        initDatabase();

        // Prevent Windows from using ';' as path separator
        System.setProperty("path.separator", ":");

        Timer hadoopObservationTimer = new Timer(true);
        hadoopObservationTimer.scheduleAtFixedRate(new HadoopObservationTask(),
                                                   HADOOP_OBSERVATION_PERIOD / 2,
                                                   HADOOP_OBSERVATION_PERIOD);
        stagingService = new StagingService();
        wpsXmlGenerator = new WpsXmlGenerator();
    }

    @Override
    public ProductSet[] getProductSets(String filter) throws ProductionException {
        // todo - load & update from persistent storage
        return new ProductSet[]{
                new ProductSet("MER_RR__1P/r03/", "MERIS_RR__1P", "All MERIS RR L1b"),
                new ProductSet("MER_RR__1P/r03/2004", "MERIS_RR__1P", "MERIS RR L1b 2004"),
                new ProductSet("MER_RR__1P/r03/2005", "MERIS_RR__1P", "MERIS RR L1b 2005"),
                new ProductSet("MER_RR__1P/r03/2006", "MERIS_RR__1P", "MERIS RR L1b 2006"),
        };
    }

    @Override
    public ProductionProcessor[] getProcessors(String filter) throws ProductionException {
        // todo - load & update from persistent storage
        return new ProductionProcessor[]{
                new ProductionProcessor("CoastColour.L2W", "MERIS CoastColour",
                                        "<parameters>\n" +
                                                "  <useIdepix>true</useIdepix>\n" +
                                                "  <landExpression>l1_flags.LAND_OCEAN</landExpression>\n" +
                                                "  <outputReflec>false</outputReflec>\n" +
                                                "</parameters>",
                                        "beam-lkn",
                                        new String[]{"1.0-SNAPSHOT"}),
        };
    }

    @Override
    public Production[] getProductions(String filter) throws ProductionException {
        return database.getProductions();
    }

    @Override
    public ProductionResponse orderProduction(ProductionRequest productionRequest) throws ProductionException {
        if ("calvalus-level3".equals(productionRequest.getProductionType())) {
            return orderL3Production(productionRequest);
        } else {
            throw new ProductionException(String.format("Unhandled production type '%s'",
                                                        productionRequest.getProductionType()));
        }
    }

    @Override
    public void cancelProductions(String[] productionIds) throws ProductionException {
        requestProductionKill(productionIds, HadoopProduction.Action.CANCEL);
    }

    @Override
    public void deleteProductions(String[] productionIds) throws ProductionException {
        requestProductionKill(productionIds, HadoopProduction.Action.DELETE);
    }

    private void requestProductionKill(String[] productionIds, HadoopProduction.Action action) throws ProductionException {
        int count = 0;
        for (String productionId : productionIds) {
            HadoopProduction hadoopProduction = database.getProduction(productionId);
            if (hadoopProduction != null) {
                hadoopProduction.setAction(action);
                try {
                    JobID jobId = hadoopProduction.getJobId();
                    org.apache.hadoop.mapred.JobID oldJobId = org.apache.hadoop.mapred.JobID.downgrade(jobId);
                    RunningJob runningJob = jobClient.getJob(oldJobId);
                    if (runningJob != null) {
                        runningJob.killJob();
                        count++;
                    }
                } catch (IOException e) {
                    // nothing to do here
                }
            }
        }
        if (count < productionIds.length) {
            throw new ProductionException(String.format("Only %d of %d production(s) have been deleted.", count, productionIds.length));
        }
    }

    // todo - remove once we have L2 requests
    public String stageProductionOutput(String productionId) throws ProductionException {
        // todo - spawn separate thread, use StagingRequest/StagingResponse/WorkStatus
        try {
            RunningJob job = jobClient.getJob(org.apache.hadoop.mapred.JobID.forName(productionId));
            String jobFile = job.getJobFile();
            // System.out.printf("jobFile = %n%s%n", jobFile);
            Configuration configuration = new Configuration(jobClient.getConf());
            configuration.addResource(new Path(jobFile));

            String jobOutputDir = configuration.get("mapred.output.dir");
            // System.out.println("mapred.output.dir = " + jobOutputDir);
            Path outputPath = new Path(jobOutputDir);
            FileSystem fileSystem = outputPath.getFileSystem(jobClient.getConf());
            FileStatus[] seqFiles = fileSystem.listStatus(outputPath, new PathFilter() {
                @Override
                public boolean accept(Path path) {
                    return path.getName().endsWith(".seq");
                }
            });

            File downloadDir = new File(stagingDirectory, outputPath.getName());
            if (!downloadDir.exists()) {
                downloadDir.mkdirs();
            }
            for (FileStatus seqFile : seqFiles) {
                Path seqProductPath = seqFile.getPath();
                System.out.println("seqProductPath = " + seqProductPath);
                StreamingProductReader reader = new StreamingProductReader(seqProductPath, jobClient.getConf());
                Product product = reader.readProductNodes(null, null);
                String dimapProductName = seqProductPath.getName().replaceFirst(".seq", ".dim");
                System.out.println("dimapProductName = " + dimapProductName);
                File productFile = new File(downloadDir, dimapProductName);
                ProductIO.writeProduct(product, productFile, ProductIO.DEFAULT_FORMAT_NAME, false);
            }
            // todo - zip or tar.gz all output DIMAPs to outputPath.getName() + ".zip" and remove outputPath.getName()
            return outputPath.getName() + ".zip";
        } catch (Exception e) {
            throw new ProductionException("Error: " + e.getMessage(), e);
        }

    }

    private ProductionResponse orderL3Production(ProductionRequest productionRequest) throws ProductionException {

        L3ProcessingRequestFactory l3ProcessingRequestFactory = new HadoopL3ProcessingRequestFactory(jobClient);
        L3ProcessingRequest l3ProcessingRequest = l3ProcessingRequestFactory.createProcessingRequest(productionRequest);

        String l3ProductionId = createL3ProductionId(productionRequest);
        String l3ProductionName = createL3ProductionName(productionRequest);
        String wpsXml = wpsXmlGenerator.createL3WpsXml(l3ProductionId, l3ProductionName, l3ProcessingRequest);

        JobID jobId = submitL3Job(wpsXml);

        boolean outputStaging = l3ProcessingRequest.getOutputStaging();
        HadoopProduction hadoopProduction = new HadoopProduction(l3ProductionId,
                                                                 l3ProductionName,
                                                                 jobId,
                                                                 outputStaging, productionRequest
        );

        L3StagingJob l3StagingJob = null;
        if (outputStaging) {
            l3StagingJob = new L3StagingJob(l3ProcessingRequest, hadoopProduction, jobClient.getConf(), wpsXml, logger);
        }

        database.addProduction(hadoopProduction);
        return new ProductionResponse(hadoopProduction);
    }

    static String createL3ProductionId(ProductionRequest productionRequest) {
        return productionRequest.getProductionType() + "-" + Long.toHexString(System.nanoTime());

    }

    static String createL3ProductionName(ProductionRequest productionRequest) {
        return String.format("Level 3 production using product set '%s' and L2 processor '%s'",
                             productionRequest.getProductionParameter("inputProductSetId"),
                             productionRequest.getProductionParameter("l2ProcessorName"));

    }

    private JobID submitL3Job(String wpsXml) throws ProductionException {
        try {
            BeamJobService beamJobService = new BeamJobService(jobClient);
            return beamJobService.submitJob(wpsXml);
        } catch (Exception e) {
            e.printStackTrace();
            throw new ProductionException("Failed to submit Hadoop job: " + e.getMessage(), e);
        }
    }

    private void initDatabase() {
        System.out.println("PRODUCTIONS_DB_FILE = " + PRODUCTIONS_DB_FILE.getAbsolutePath());
        try {
            database.load(PRODUCTIONS_DB_FILE);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to load productions: " + e.getMessage(), e);
        }
    }


    private Map<JobID, JobStatus> getJobStatusMap() throws IOException {
        JobStatus[] jobStatuses = jobClient.getAllJobs();
        HashMap<JobID, JobStatus> jobStatusMap = new HashMap<JobID, JobStatus>();
        for (JobStatus jobStatus : jobStatuses) {
            jobStatusMap.put(jobStatus.getJobID(), jobStatus);
        }
        return jobStatusMap;
    }

    private void updateProductionsState() throws IOException {
        Map<JobID, JobStatus> jobStatusMap = getJobStatusMap();
        HadoopProduction[] productions = database.getProductions();

        // Update state of all registered productions
        for (HadoopProduction production : productions) {
            JobStatus jobStatus = jobStatusMap.get(production.getJobId());
            production.setProductionStatus(jobStatus);
        }

        // Now try to delete productions
        for (HadoopProduction production : productions) {
            if (HadoopProduction.Action.DELETE.equals(production.getAction())) {
                if (production.getProcessingStatus().isDone()) {
                    database.removeProduction(production);
                }
            }
        }

        // Copy result to staging area
        for (HadoopProduction production : productions) {
            if (production.isOutputStaging()
                    && production.getProcessingStatus().getState() == ProductionState.COMPLETED
                    && production.getStagingStatus().getState() == ProductionState.WAITING) {
                production.setStagingStatus(new ProductionStatus(ProductionState.WAITING));
                // todo - stagingService.stageProduction(production, jobClient.getConf());
            }
        }

        // write to persistent storage
        database.store(PRODUCTIONS_DB_FILE);
    }

    private class HadoopObservationTask extends TimerTask {
        private long lastLog;

        @Override
        public void run() {
            try {
                updateProductionsState();
            } catch (IOException e) {
                logError(e);
            }
        }

        private void logError(IOException e) {
            long time = System.currentTimeMillis();
            if (time - lastLog > 120 * 1000L) {
                logger.log(Level.SEVERE, "Failed to update production state:" + e.getMessage(), e);
                lastLog = time;
            }
        }
    }
}
