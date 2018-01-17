# SendAlarm

REQUIREMENT:
	- Kafka and Zookeeper Server installed and running;
	- Plugin and Convert running;
 
 IAS CONFIGURATION:
  Before run the class you have to configure the CDB (in this example testCDB/CDB) you have to modify ASCE, DASU, IASIO and TF.
 
 The main class (DasuOneASCETest, take from https://github.com/IntegratedAlarmSystem-Group/ias, created by Alessandro Caproni) start the dasu 
 and check if the value is an alarm. The alarm is defined in the CDB file.
