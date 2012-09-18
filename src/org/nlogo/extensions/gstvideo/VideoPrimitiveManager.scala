package org.nlogo.extensions.gstvideo

import org.gstreamer.{ ElementFactory, elements }, elements.AppSink
import org.nlogo.api.{ Argument, Context, ExtensionException, Syntax}

/**
 * Created with IntelliJ IDEA.
 * User: Jason
 * Date: 9/18/12
 * Time: 1:17 PM
 */

trait VideoPrimitiveManager {

  protected lazy val balance = ElementFactory.make("videobalance", "balance")
  protected lazy val appSink = initSink()

  def unload() {
    balance.dispose()
  }

  protected def initSink() : AppSink = {
    val sink = ElementFactory.make("appsink", "sink") match {
      case appSink: AppSink => appSink
      case other            => throw new ExtensionException("Invalid sink type created: class == " + other.getClass.getName)
    }
    sink.set("max-buffers", 1)
    sink.set("drop", true)
    sink
  }


  object SetContrast extends VideoCommand {
    override def getSyntax = Syntax.commandSyntax(Array[Int](Syntax.NumberType))
    override def perform(args: Array[Argument], context: Context) {
      validateAndSet("contrast", args(0).getDoubleValue, 0, 2)
    }
  }

  object SetBrightness extends VideoCommand {
    override def getSyntax = Syntax.commandSyntax(Array[Int](Syntax.NumberType))
    override def perform(args: Array[Argument], context: Context) {
      validateAndSet("brightness", args(0).getDoubleValue, -1, 1)
    }
  }

  object SetHue extends VideoCommand {
    override def getSyntax = Syntax.commandSyntax(Array[Int](Syntax.NumberType))
    override def perform(args: Array[Argument], context: Context) {
      validateAndSet("hue", args(0).getDoubleValue, -1, 1)
    }
  }

  object SetSaturation extends VideoCommand {
    override def getSyntax = Syntax.commandSyntax(Array[Int](Syntax.NumberType))
    override def perform(args: Array[Argument], context: Context) {
      validateAndSet("saturation", args(0).getDoubleValue, 0, 2)
    }
  }

  private def validateAndSet(settingName: String, value: Double, min: Double, max: Double) {
    if (value >= min && value <= max)
      balance.set(settingName, value)
    else
      throw new ExtensionException("invalid %s value: %f (must be within [%f, %f])".format(settingName, value, min, max))
  }

}