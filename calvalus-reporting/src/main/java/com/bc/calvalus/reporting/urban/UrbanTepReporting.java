package com.bc.calvalus.reporting.urban;

import com.bc.calvalus.commons.CalvalusLogger;
import com.bc.calvalus.reporting.common.Report;
import com.bc.calvalus.reporting.common.Reporter;
import com.bc.calvalus.reporting.common.ReportingConnection;
import com.bc.calvalus.reporting.common.StatusHandler;
import com.bc.calvalus.reporting.common.WpsConnection;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Properties;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.logging.Logger;

/**
 * @author martin
 */
public class UrbanTepReporting implements Reporter {

    static final Logger LOGGER = CalvalusLogger.getLogger();
    private final String configPath;
    private final Properties config = new Properties();
    private ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(1);
    private ReportingConnection reportingConnection = new ReportingConnection(this);
    private AccountingConnection accountingConnection = new AccountingConnection(this);
    private WpsConnection wpsConnection = new WpsConnection(this);
    private StatusHandler statusHandler = new StatusHandler(this);

    public UrbanTepReporting(String configPath) {
        this.configPath = configPath;
    }

    @Override
    public String getName() {
        return "urbantep";
    }

    @Override
    public Properties getConfig() {
        return config;
    }

    @Override
    public void process(Report report) {
        switch (report.state) {
        case NOT_YET_RETRIEVED:
            getStatusHandler().setRunning(report.job, report.creationTime);
        case NEW:
            getReportingConnection().retrieve(report);
            break;
        case NOT_YET_ACCOUNTED:
            getStatusHandler().setRunning(report.job, report.creationTime);
        case RETRIEVED:
            getAccountingConnection().send(report);
            break;
        default:
            LOGGER.warning("report " + report.job + " in state " + report.state + " cannot be handled");
        }
    }

    @Override
    public StatusHandler getStatusHandler() {
        return statusHandler;
    }

    @Override
    public ScheduledThreadPoolExecutor getTimer() {
        return timer;
    }

    @Override
    public void setTimer(ScheduledThreadPoolExecutor timer) {
        this.timer = timer;
    }

    public ReportingConnection getReportingConnection() {
        return reportingConnection;
    }

    public AccountingConnection getAccountingConnection() {
        return accountingConnection;
    }

    public static void main(String[] args) {
        try {
            new UrbanTepReporting(args.length > 0 ? args[0] : "etc/urbantep.properties").run();
        } catch (Exception e) {
            LOGGER.severe("UrbanTepReporting start failed: " + e.getMessage());
            System.exit(1);
        }
    }

    public void run() throws Exception {
        initConfiguration();
        statusHandler.initReport();
        wpsConnection.run();
    }

    void initConfiguration() throws IOException {
        try {
            try (Reader in = new FileReader(configPath)) {
                config.load(in);
            }
        } catch (IOException e) {
            throw new IOException("failed to read configuration from " + configPath + ": " + e.getMessage(), e);
        }
    }
}
