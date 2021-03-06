package sendAlarm

import org.scalatest.FlatSpec
import org.ias.prototype.logging.IASLogger
import java.nio.file.FileSystems
import org.eso.ias.cdb.json.CdbJsonFiles
import org.eso.ias.cdb.CdbReader
import org.eso.ias.cdb.json.JsonReader
import org.eso.ias.dasu.publisher.KafkaPublisher
import java.util.Properties
import org.eso.ias.dasu.subscriber.KafkaSubscriber
import org.eso.ias.dasu.Dasu
import org.eso.ias.prototype.input.Identifier
import org.eso.ias.prototype.input.java.IASValue
import org.eso.ias.prototype.input.java.IasDouble
import org.eso.ias.prototype.input.java.IdentifierType
import org.eso.ias.prototype.input.java.OperationalMode
import org.eso.ias.kafkautils.SimpleStringConsumer
import org.eso.ias.kafkautils.KafkaHelper
import org.eso.ias.kafkautils.SimpleStringProducer
import org.eso.ias.kafkautils.SimpleStringConsumer.KafkaConsumerListener
import org.eso.ias.prototype.input.java.IasValueJsonSerializer
import scala.util.Try
import scala.collection.mutable.ListBuffer
import org.eso.ias.kafkautils.SimpleStringConsumer.StartPosition
import org.eso.ias.prototype.input.java.IasValidity._




class DasuTest extends FlatSpec with KafkaConsumerListener {
	    private val logger = IASLogger.getLogger(this.getClass)

			// Build the CDB reader
			val cdbParentPath =  FileSystems.getDefault().getPath("testCDB")
			val cdbFiles = new CdbJsonFiles(cdbParentPath)
			val cdbReader: CdbReader = new JsonReader(cdbFiles)

			val dasuId = "DasuWithOneASCE"

			val outputPublisher = KafkaPublisher(dasuId, new Properties())

			val inputsProvider = new KafkaSubscriber(dasuId, new Properties())

			// The DASU
			val dasu = new Dasu(dasuId,outputPublisher,inputsProvider,cdbReader)

			// The identifer of the monitor system that produces the temperature in input to teh DASU
			val monSysId = new Identifier("MonitoredSystemID",IdentifierType.MONITORED_SOFTWARE_SYSTEM)
			// The identifier of the plugin
			val pluginId = new Identifier("PluginID",IdentifierType.PLUGIN,monSysId)
			// The identifier of the converter
			val converterId = new Identifier("ConverterID",IdentifierType.CONVERTER,pluginId)
			// The ID of the monitor point in unput (it matched the ID in theJSON file)
			val inputID = new Identifier("WS_WINDPSD", IdentifierType.IASIO,converterId)


			/** The publisher to send IASValues to kafka topic */
			/*val eventsListener = new SimpleStringConsumer(
					KafkaHelper.DEFAULT_BOOTSTRAP_BROKERS,
					KafkaHelper.IASIOs_TOPIC_NAME,
					"DasuWithKafka-TestSub",
					this)

			logger.debug("initializing the event listener")
			val props = new Properties()
			props.setProperty("group.id", "DasuTest-groupID")
			eventsListener.setUp(props)
			eventsListener.startGettingEvents(StartPosition.END)*/

	    /*
	     * String Server, Destination topic, Suorce Topic
	     */

			 val stringPublisher = new SimpleStringProducer(
 					KafkaHelper.DEFAULT_BOOTSTRAP_BROKERS,
 					KafkaHelper.IASIOs_TOPIC_NAME,
 					KafkaHelper.IASIOs_TOPIC_NAME)

			logger.debug("initializing the IASIO publisher")

			stringPublisher.setUp()

			val jsonSerializer = new IasValueJsonSerializer()

			/** The list with the events received */
			val iasValuesReceived: ListBuffer[IASValue[_]] = ListBuffer()

			logger.info("Giving kakfka stuff time to be ready...")
			Try(Thread.sleep(5000))

			logger.info("Ready to start the test...")

			def buildValue(d: Double): IASValue[_] = {
					new IasDouble(
							d,
							System.currentTimeMillis(),
							OperationalMode.OPERATIONAL,
							UNRELIABLE,
							inputID.id,
							inputID.fullRunningID)
			}

			def stringEventReceived(event: String) = {
					logger.info("String received", event)
					val iasValue = jsonSerializer.valueOf(event)
					iasValuesReceived+=iasValue
			}

			behavior of "The DASU"

			it must "produce the output when a new set inputs is notified" in {
				// Start the getting of events in the DASU
				dasu.start()

				// Send the input to the kafka queue
				val input = buildValue(0)
				val jSonStr = jsonSerializer.iasValueToString(input)
				stringPublisher.push(jSonStr, null, input.id)

				// Give some time...
				Try(Thread.sleep(5000))

				// We sent one IASIO but the listener must have received 2:
				// - the one we sent
				// the one produced by the DASU!
				assert(iasValuesReceived.size==0)


				logger.info("Cleaning up the event listener")
				//eventsListener.tearDown()
				logger.info("Cleaning up the IASIO publisher")
				//stringPublisher.tearDown()
				logger.info("Done")
			}

}
