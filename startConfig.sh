#!/bin/bash 



#format namenode
hdfs namenode -format MyCluster

#add file to hdfs
hdfs dfs -put /opt/AzureDataset/vm_cpu_readings-file-X-of-125.csv /azure/

sbin/stop-all.sh
sbin/start-all.sh


mapred --daemon start historyserver

hdfs dfsadmin -printTopology
hdfs balancer
hdfs dfsadmin -report
hdfs dfs -rmdir --ignore-fail-on-non-empty  /user/root/output
hdfs dfs -mkdir /azure
