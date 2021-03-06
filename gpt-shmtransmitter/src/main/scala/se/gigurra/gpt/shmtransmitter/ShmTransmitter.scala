package se.gigurra.gpt.shmtransmitter

import se.culvertsoft.mnet.{Message, NodeSettings}
import se.culvertsoft.mnet.backend.WebsockBackendSettings
import se.culvertsoft.mnet.client.MNetClient
import se.gigurra.gpt.common.{NetworkNames, ReadConfigFile, SaveConfigFile, Serializer, SharedMemory}
import se.gigurra.gpt.model.shm.common.ShmMsg
import se.gigurra.gpt.model.shm.transmitter.ShmTransmitterCfg

import scala.collection.JavaConversions.{asScalaBuffer, bufferAsJavaList}
import scala.collection.mutable.{ArrayBuffer, HashMap}

object ShmTransmitter {

  val readBuffers = new HashMap[SharedMemory, Array[Byte]]
  val msgs = new HashMap[SharedMemory, Message]

  def openShms(names: Seq[String]) = {

    def tryOpenShm(name: String) = {
      new SharedMemory(name, 0, false)
    }

    println(s"Opening ${names.length} shared memories:")
    val shms = new ArrayBuffer[SharedMemory]()
    for (s <- names) {
      print(s"Opening '$s'...")
      var shm = tryOpenShm(s)
      while (!shm.valid) {
        shm = tryOpenShm(s)
        Thread.sleep(1000)
      }
      println(s"done! (${shm.size} bytes)")
      shms += shm
      readBuffers.put(shm, new Array[Byte](shm.size))
    }
    shms
  }

  def main(args: Array[String]) {

    val cfgFileName = "gpt-shmtransmitter-cfg.json"
    val cfg = ReadConfigFile[ShmTransmitterCfg](cfgFileName).getOrElse(new ShmTransmitterCfg)
    SaveConfigFile(cfgFileName, cfg)

    val nodeSettings = new NodeSettings().setName(NetworkNames.SHM_TRANSMITTER)
    val wsSettings = new WebsockBackendSettings().unsetListenPort()
    wsSettings.getConnectTo().addAll(cfg.getTargets.map(se.gigurra.gpt.common.NetworkAddr2Url.apply))

    val client = new MNetClient(wsSettings, nodeSettings).start()
    val shms = openShms(cfg.getShms)

    while (true) {

      // Create shm msgs
      for (shm <- shms) {

        val readBuf = readBuffers(shm)
        shm.read(readBuf, readBuf.length)

        val msg = Serializer.writeBinary(new ShmMsg()
          .setData(readBuffers(shm))
          .setName(shm.name)
          .setSize(shm.size))
          .setSenderId(client.id)
        msgs.put(shm, msg)
      }

      // Send shms
      for (route <- client.getRoutes) {
        if (route.isConnected && !route.hasBufferedData && route.name == NetworkNames.SHM_RECEIVER) {
          for (shm <- shms) {
            route.send(msgs(shm).setTargetId(route.endpointId))
          }
        }
      }

      Thread.sleep(15)

    }

  }

}
