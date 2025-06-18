// USBParams.scala
package usb

import chisel3._
import chisel3.util._
import chisel3.experimental.{Analog, IntParam, BaseModule,attach}
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.config.{Parameters, Field, Config}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.regmapper.{HasRegMap, RegField}
import freechips.rocketchip.subsystem.BaseSubsystem
import freechips.rocketchip.tilelink._
import freechips.rocketchip.amba.axi4.AXI4Buffer
import freechips.rocketchip.util.UIntIsOneOf

// === USB IO Definition ===
class FPGATopUsbCameraIO extends Bundle {
  val clk50mhz     = Input(Clock())
  val button       = Input(Bool())
  val led          = Output(Bool())
  val usb_dp_pull  = Output(Bool())
  val usb_dp       = Analog(1.W)
  val usb_dn       = Analog(1.W)
  val uart_tx      = Output(Bool())
}
// =========================

trait HasFPGATopUsbCameraIO extends BaseModule {
  val io = IO(new FPGATopUsbCameraIO())
}


// === BlackBox wrapper ===
class FPGATopUsbCamera extends BlackBox with HasBlackBoxResource with HasFPGATopUsbCameraIO  {
  addResource("/vsrc/FPGATopUsbCamera.v")
  addResource("/vsrc/usb_camera_top.v")
  addResource("/vsrc/usbfs_core_top.v")
  addResource("/vsrc/usbfs_packet_rx.v")
  addResource("/vsrc/usbfs_packet_tx.v")
  addResource("/vsrc/usbfs_bitlevel.v")
  addResource("/vsrc/usbfs_transaction.v")
  addResource("/vsrc/usbfs_debug_monitor.v")
  addResource("/vsrc/usbfs_debug_uart_tx.v")
}
// =========================

// === USB Param===
case class USBParams(address: BigInt = 0x10016000L, useAXI4: Boolean = false, useBlackBox: Boolean = true)

// === Config / Key ===
case object USBKey extends Field[Option[USBParams]](None)
// =========================

trait USBTOPIO {
  // 外部 FPGA 頂層與 BlackBox 共享的訊號，把blackbox訊號接給頂層 
  val clk50mhz    = Input(Clock())
  val button      = Input(Bool())
  val led         = Output(Bool())
  val usb_dp_pull = Output(Bool())
  val usb_dp      = Analog(1.W)
  val usb_dn      = Analog(1.W)
  val uart_tx     = Output(Bool())
}

trait USBModule extends HasRegMap { 
  val io: USBTOPIO
  implicit val p: Parameters
    // === 1. 每個暴露給AXI的REG ===
  val ledReg          = RegInit(false.B)
  val usb_dp_pullReg  = RegInit(false.B)
  val buttonReg  = Wire(Bool())
  /*
  val usb_dp_out = RegInit(false.B)
  val usb_dp_oe  = RegInit(false.B)
  val usb_dp_in  = Wire(Bool())

  val usb_dn_out = RegInit(false.B)
  val usb_dn_oe  = RegInit(false.B)
  val usb_dn_in  = Wire(Bool())
  */
  val uart_txReg = Wire(Bool())
  /* === 2. BlackBox === */
  val inst = Module(new FPGATopUsbCamera)
  //讀入外部INPUT
  inst.io.clk50mhz := io.clk50mhz // assign 內部 = 外部
  buttonReg := io.button // 直接把頂層的button接到reg
  inst.io.button      := buttonReg // 把頂層的button接到blackbox
  // 把blackbox output讀出來
  ledReg := inst.io.led
  io.led := ledReg

  usb_dp_pullReg := inst.io.usb_dp_pull
  io.usb_dp_pull := usb_dp_pullReg

  uart_txReg := inst.io.uart_tx
  io.uart_tx := uart_txReg
    // === 4. Analog attach 模擬
  attach(inst.io.usb_dp, io.usb_dp)
  attach(inst.io.usb_dn, io.usb_dn)

  // === regmap ===
  regmap(
    0x00 -> Seq(RegField(1, ledReg)),         // write: 控制 LED
    0x04 -> Seq(RegField.r(1, buttonReg)),    // read: 讀按鈕

    0x08 -> Seq(RegField(1, usb_dp_pullReg)), // write: 拉高上拉
    /*
    0x10 -> Seq(RegField(1, usb_dp_out)),
    0x14 -> Seq(RegField(1, usb_dp_oe)),
    0x18 -> Seq(RegField.r(1, usb_dp_in)),

    0x20 -> Seq(RegField(1, usb_dn_out)),
    0x24 -> Seq(RegField(1, usb_dn_oe)),
    0x28 -> Seq(RegField.r(1, usb_dn_in)),
    */
    0x30 -> Seq(RegField.r(1, uart_txReg))    // read: debug 資訊
  )
}

// === AXI Register Router ===

class USBTL(params: USBParams, beatBytes: Int)(implicit p: Parameters)
  extends TLRegisterRouter(
    params.address, "usb", Seq("ucbbar,usb"),
    beatBytes = beatBytes)(
      new TLRegBundle(params, _) with USBTOPIO)(
      new TLRegModule(params, _, _) with USBModule)

class USBAXI4(params: USBParams, beatBytes: Int)(implicit p: Parameters)
  extends AXI4RegisterRouter(
    params.address,// AXI暫不支援在device tree
    beatBytes = beatBytes)(
      new AXI4RegBundle(params, _) with USBTOPIO)(
      new AXI4RegModule(params, _, _) with USBModule)

// === Subsystem Integration ===
trait CanHavePeripheryUSB { this: BaseSubsystem =>
    private val portName = "usb"
    val usb = p(USBKey) match {    
      case Some(params) => {
        if (params.useAXI4) {
            val usb = LazyModule(new USBAXI4(params, sbus.beatBytes))
            sbus.coupleTo("usb") {
                usb.node :=
                AXI4Buffer() :=
                TLToAXI4() :=
                TLFragmenter(sbus.beatBytes, sbus.blockBytes, holdFirstDeny = true) := _
            }
            Some(usb)
        } else {
         val usb = LazyModule(new USBTL(params, pbus.beatBytes)(p))
          pbus.toVariableWidthSlave(Some(portName)) { usb.node }
          Some(usb)       
        }
      }
      case None => None
    }
}

trait CanHavePeripheryUSBModuleImp extends LazyModuleImp {
  val outer: CanHavePeripheryUSB          // Subsystem trait

  // 只有當 Config 定義了 USBKey 時才會進來
  outer.usb.foreach { usbLM =>
    // === 1. 把 BlackBox 端口全部複製一份到頂層 ===
    val usb_io = IO(new FPGATopUsbCameraIO)

    // === 2. clock / reset / 數位腳位直接對接 ===
    // 宣告io寫進去IOTOP
    usbLM.module.io.clk50mhz := usb_io.clk50mhz
    usbLM.module.io.button   := usb_io.button

    // 內部輸出接出來
    usb_io.led         := usbLM.module.io.led
    usb_io.usb_dp_pull := usbLM.module.io.usb_dp_pull
    usb_io.uart_tx     := usbLM.module.io.uart_tx

    // === 3. 差分資料腳位用 attach ===
    attach(usbLM.module.io.usb_dp, usb_io.usb_dp)
    attach(usbLM.module.io.usb_dn, usb_io.usb_dn)

    // === (選擇) 4. 把無用腳位處理掉 ===
    // 如果覺得 clock/reset 不該由外界給，也可以在這邊直接接 system clock。
  }
}


class WithUSB(useAXI4: Boolean = true, useBlackBox: Boolean = true) extends Config((site, here, up) => {
  case USBKey => Some(USBParams(useAXI4 = useAXI4, useBlackBox = useBlackBox))
})
