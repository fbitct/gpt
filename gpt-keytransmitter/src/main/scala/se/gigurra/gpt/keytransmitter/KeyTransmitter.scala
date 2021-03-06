package se.gigurra.gpt.keytransmitter

import com.sun.jna.platform.win32.{Kernel32, User32}
import com.sun.jna.platform.win32.WinDef.{LRESULT, WPARAM}
import com.sun.jna.platform.win32.WinUser.{KBDLLHOOKSTRUCT, LowLevelKeyboardProc, MSG, WH_KEYBOARD_LL}
import se.culvertsoft.mnet.client.MNetClient
import se.gigurra.gpt.common.{NetworkNames, ReadConfigFile, SaveConfigFile, Serializer}
import se.gigurra.gpt.model.keys.common.KeyMessage
import se.gigurra.gpt.model.keys.transmitter.KeyTransmitterCfg

import scala.collection.JavaConversions.asScalaBuffer

object KeyTransmitter {

  def main(args: Array[String]) {

    val cfgFileName = "gpt-keytransmitter-cfg.json"
    val cfg = ReadConfigFile[KeyTransmitterCfg](cfgFileName).getOrElse(new KeyTransmitterCfg)
    SaveConfigFile(cfgFileName, cfg)

    val client = new MNetClient(
      NetworkNames.KEY_TRANSMITTER,
      cfg.getTarget.getIp,
      cfg.getTarget.getPort).start() // port 8052

    val lpfn = new LowLevelKeyboardProc() {
      override def callback(nCode: Int, wPar: WPARAM, lp: KBDLLHOOKSTRUCT): LRESULT = {

        val msg = new KeyMessage
        msg.setValue(wPar.intValue)
        msg.setFlags(lp.flags)
        msg.setScanCode(lp.scanCode)
        msg.setVCode(lp.vkCode)

        for (route <- client.getRoutes) {
          if (route.isConnected && route.name == NetworkNames.KEY_RECEIVER) {
            route.send(
                Serializer.writeJson(msg)
                .setTargetId(route.endpointId)
                .setSenderId(client.id))
          }
        }

        return User32.INSTANCE.CallNextHookEx(null, nCode, wPar, lp.getPointer())
      }

    }

    val hModule = Kernel32.INSTANCE.GetModuleHandle(null)
    val hHook = User32.INSTANCE.SetWindowsHookEx(WH_KEYBOARD_LL, lpfn, hModule, 0)
    if (hHook == null) {
      System.err.println("Failed to create keyboard hook, bailing!")
      System.exit(1)
    }

    val msg = new MSG()
    var quit = false
    while (!quit) {
      val result = User32.INSTANCE.GetMessage(msg, null, 0, 0)
      if (result == -1 || result == 0) {
        System.out.println("Exiting, GetMessage returned " + result)
        quit = true
      } else {
        User32.INSTANCE.TranslateMessage(msg)
        User32.INSTANCE.DispatchMessage(msg)
      }
      Thread.sleep(1)
    }

    if (User32.INSTANCE.UnhookWindowsHookEx(hHook)) {
      System.out.println("Unhooked")
    }

    System.exit(0)

  }
}