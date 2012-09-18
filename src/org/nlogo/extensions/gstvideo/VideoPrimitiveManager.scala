package org.nlogo.extensions.gstvideo

import org.gstreamer.ElementFactory
import org.nlogo.api.{ Argument, Context, ExtensionException, Syntax}

/**
 * Created with IntelliJ IDEA.
 * User: Jason
 * Date: 9/18/12
 * Time: 1:17 PM
 */

trait VideoPrimitiveManager {

  protected lazy val balance = ElementFactory.make("videobalance", "balance")

  def unload() {
    balance.dispose()
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