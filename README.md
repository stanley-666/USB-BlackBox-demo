[The repo of free USB IP](https://github.com/WangXuan95/FPGA-USB-Device)
Environment : Chipyard v1.8.1

## build
push this directory in ~chipyard/generators/
```
git clone https://github.com/stanley-666/USB-BlackBox-demo.git
```

## Modify ~/chipyard/build.sbt
add "usb" like below 
```scala!
lazy val chipyard = (project in file("generators/chipyard"))
  .dependsOn(testchipip, rocketchip, boom, hwacha, sifive_blocks, sifive_cache, iocell,
    sha3, // On separate line to allow for cleaner tutorial-setup patches
    dsptools, `rocket-dsp-utils`,
    gemmini, icenet, tracegen, cva6, nvdla, sodor, ibex, fft_generator,
    constellation, mempress, usb)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)
```
then
```bash!
sbt compile
```
## Modify DigitalTop 
```
cd ~/chipyard/src/main/scala/
code DigitalTop.scala
```
```scala!
import usb._
class DigitalTop(implicit p: Parameters) extends ChipyardSystem
  with usb.CanHavePeripheryUSB // new usb
  ...

class DigitalTopModule[+L <: DigitalTop](l: L) extends ChipyardSystemModule(l)
  with usb.CanHavePeripheryUSBModuleImp 
  ...
```
## Add Config
```bash!
cd ~/chipyard/src/main/scala/config
code RocketConfigs.scala
```
```scala!
...
import usb._
...
...
...
class USBAXI4BlackBoxRocketConfig extends Config(
  new WithUSB(useAXI4=false, useBlackBox=true) ++ // can change to use AXI4 or Tilelink
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig
)
```


