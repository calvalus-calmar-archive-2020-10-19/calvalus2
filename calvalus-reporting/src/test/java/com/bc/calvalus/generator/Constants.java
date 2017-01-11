package com.bc.calvalus.generator;

/**
 *
 */
public class Constants {
    public static final String XMLSourceCounter = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" +
            "<jobCounters>\n" +
            "    <id>job_1481485063251_3649</id>\n" +
            "    <counterGroup>\n" +
            "        <counterGroupName>org.apache.hadoop.mapreduce.FileSystemCounter</counterGroupName>\n" +
            "        <counter>\n" +
            "            <name>HDFS_BYTES_READ</name>\n" +
            "            <totalCounterValue>449694437376</totalCounterValue>\n" +
            "            <mapCounterValue>449694437376</mapCounterValue>\n" +
            "            <reduceCounterValue>0</reduceCounterValue>\n" +
            "        </counter>\n" +
            "        <counter>\n" +
            "            <name>HDFS_BYTES_WRITTEN</name>\n" +
            "            <totalCounterValue>466511729804</totalCounterValue>\n" +
            "            <mapCounterValue>466511729804</mapCounterValue>\n" +
            "            <reduceCounterValue>0</reduceCounterValue>\n" +
            "        </counter>\n" +
            "        <counter>\n" +
            "            <name>What</name>\n" +
            "            <totalCounterValue>466511729804</totalCounterValue>\n" +
            "            <mapCounterValue>466511729804</mapCounterValue>\n" +
            "            <reduceCounterValue>0</reduceCounterValue>\n" +
            "        </counter>\n" +
            "    </counterGroup>\n" +
            "</jobCounters>";


    public static final String XMLSourceConf = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" +
            "<conf>\n" +
            "    <path>\n" +
            "        hdfs://calvalus:8020/tmp/hadoop-yarn/staging/history/done/2016/12/21/000004/job_1481485063251_4637_conf.xml\n" +
            "    </path>\n" +
            "    <property>\n" +
            "        <name>calvalus.bundle</name>\n" +
            "        <value>local</value>\n" +
            "        <source>mapred-default.xml</source>\n" +
            "        <source>job.xml</source>\n" +
            "        <source>count </source>\n" +
            "    </property>\n" +
            "    <property>\n" +
            "        <name>calvalus.l2.operator</name>\n" +
            "        <value>5000</value>\n" +
            "        <source>hdfs-default.xml</source>\n" +
            "        <source>job.xml</source>\n" +
            "        <source>what</source>\n" +
            "    </property>\n" +
            "    <property>\n" +
            "        <name>calvalus.input.pathPatterns</name>\n" +
            "        <value>10</value>\n" +
            "        <source>mapred-default.xml</source>\n" +
            "        <source>job.xml</source>\n" +
            "        <source>count </source>\n" +
            "    </property>\n" +
            "    <property>\n" +
            "        <name>input</name>\n" +
            "        <value>10</value>\n" +
            "        <source>mapred-default.xml</source>\n" +
            "        <source>job.xml</source>\n" +
            "        <source>count </source>\n" +
            "    </property>\n" +
            "    <property>\n" +
            "        <name>calvalus</name>\n" +
            "        <value>10</value>\n" +
            "        <source>mapred-default.xml</source>\n" +
            "        <source>job.xml</source>\n" +
            "        <source>count </source>\n" +
            "    </property>\n" +
            "    <property>\n" +
            "        <name>Patterns</name>\n" +
            "        <value>10</value>\n" +
            "        <source>mapred-default.xml</source>\n" +
            "        <source>job.xml</source>\n" +
            "        <source>count </source>\n" +
            "    </property>\n" +
            "</conf>";

    public static String HELP_INFO = "<generate-calvalus-report> --help\n" +
            "Usage: generate-calvalus-report [COMMAND] [OPTION\n" +
            "       generate-calvalus-report [ --help | -v | --version ]\n" +
            "\n" +
            "Options:\n" +
            "\n" +
            "  -h, --help                            Print usage\n" +
            "  -i, --interval                        Time interval\n" +
            "  -o, --output-file-path                Location to save the generate report\n" +
            "  -v, --version                         Print version information and quit\n" +
            "\n" +
            "Commands:\n" +
            "    start            Start generating with the default of 30 minutes\n" +
            "Run 'generate-calvalus-report COMMAND --help' for more information on a command.\n";

}