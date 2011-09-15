package com.bc.calvalus.production.hadoop;

import com.bc.calvalus.commons.ProcessState;
import com.bc.calvalus.commons.ProcessStatus;
import com.bc.calvalus.processing.ma.MAWorkflowItem;
import com.bc.calvalus.production.Production;
import com.bc.calvalus.production.ProductionStaging;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.FileUtil;
import org.apache.hadoop.fs.Path;
import org.esa.beam.util.io.FileUtils;

import java.io.File;

/**
 * The staging job for match-up analysis (MA) results.
 *
 * @author Norman
 */
class MAStaging extends ProductionStaging {

    private final Configuration hadoopConfiguration;
    private final File stagingDir;

    public MAStaging(Production production,
                     Configuration hadoopConfiguration,
                     File stagingAreaPath) {
        super(production);
        this.hadoopConfiguration = hadoopConfiguration;
        this.stagingDir = new File(stagingAreaPath, production.getStagingPath());
    }

    @Override
    public void performStaging() throws Throwable {
        Production production = getProduction();
        production.setStagingStatus(new ProcessStatus(ProcessState.RUNNING, 0.0F, ""));
        MAWorkflowItem workflow = (MAWorkflowItem) production.getWorkflow();
        if (!stagingDir.exists()) {
            stagingDir.mkdirs();
        }

        Path remoteOutputDir = new Path(workflow.getOutputDir());
        FileSystem fileSystem = remoteOutputDir.getFileSystem(hadoopConfiguration);

        // Simply copy entire content of remoteOutputDir
        FileStatus[] fileStatuses = fileSystem.globStatus(new Path(remoteOutputDir, "*.*"));
        Path[] paths = FileUtil.stat2Paths(fileStatuses);
        if (paths != null) {
            for (int i = 0; i < paths.length; i++) {
                Path path = paths[i];
                FileUtil.copy(fileSystem,
                              path,
                              new File(stagingDir, path.getName()),
                              false, hadoopConfiguration);
                production.setStagingStatus(new ProcessStatus(ProcessState.RUNNING, (i + 1.0F) / paths.length, path.getName()));
            }
        }

        zip(stagingDir, new File(stagingDir, stagingDir.getName() + ".zip"));

        production.setStagingStatus(new ProcessStatus(ProcessState.COMPLETED, 1.0F, ""));
    }

    @Override
    public void cancel() {
        super.cancel();
        FileUtils.deleteTree(stagingDir);
        getProduction().setStagingStatus(new ProcessStatus(ProcessState.CANCELLED));
    }
}
