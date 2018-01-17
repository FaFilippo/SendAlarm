package org.eso.ias.prototype.input

import org.eso.ias.prototype.utils.ISO8601Helper
import org.eso.ias.prototype.input.java.OperationalMode
import org.eso.ias.prototype.input.java.IASTypes._
import org.eso.ias.prototype.input.java.IASTypes
import org.eso.ias.prototype.input.java.IasValidity
import org.eso.ias.prototype.input.java.IasValidity._
import org.eso.ias.prototype.input.java.AlarmSample
import org.eso.ias.prototype.input.java.IASValue
import org.eso.ias.prototype.input.java.IdentifierType

/**
 * A  <code>InOut</code> holds the value of an input or output 
 * of the IAS.
 * Objects of this type constitutes both the input of ASCEs and the output 
 * they produce.
 * 
 * The type of a InOut can be a double, an integer, an
 * array of integers and many other customized types.
 * 
 * The actual value is an Option because there is no
 * value associated before it comes for example from the HW. 
 * Nevertheless the <code>InOut</code> exists
 * 
 * The refresh rate is used in two ways: to assess the validity for the inputs 
 * coming from external monitored systems or generated by IAS DASUs.
 * The DASU uses the refreshRate to send the IASIO in output back to
 * the BSDB.
 * The refrshRate of IASIOs generated by ASCEs running inside a DASU that are not
 * the output of DASUs are ignored. 
 * 
 * <code>InOut</code> is immutable.
 * 
 * @param actualValue: the actual value of this InOut (can be undefined) 
 * @param id: The unique ID of the monitor point
 * @param refreshRate: The expected refresh rate (msec) of this monitor point
 *                     (to be used to assess its validity)
 * @param mode: The operational mode
 * @param validity: The validity of the monitor point
 * @param iasType: is the IAS type of this InOut
 * 
 * @see IASType
 * 
 * @author acaproni
 */
case class InOut[A](
    value: Option[_ >: A],
    val id: Identifier,
    val refreshRate: Int,    
    val mode: OperationalMode,
    val validity: Validity,
    val iasType: IASTypes) {
  require(Option[Identifier](id).isDefined,"The identifier must be defined")
  require(refreshRate>=InOut.MinRefreshRate,"Invalid refresh rate (too low): "+refreshRate)
  require(Option(validity).isDefined,"Undefined validity is not allowed")
  require(Option[IASTypes](iasType).isDefined,"The type must be defined")
  
  val  actualValue: InOutValue[A] = new InOutValue(value)
  if (actualValue.value.isDefined) require(InOut.checkType(actualValue.value.get,iasType),"Type mismatch: ["+actualValue.value.get+"] is not "+iasType)
  
  override def toString(): String = {
    "Monitor point " + id.toString() +" of IAS type " +iasType+"\n\t" +  
    mode.toString() + "\n\t" +
    validity.toString() +"\n\t" +
    (if (actualValue.value.isEmpty) "No value" else "Value: "+actualValue.value.get.toString())
  }
  
  /**
   * Update the mode of the monitor point
   * 
   * @param newMode: The new mode of the monitor point
   */
  def updateMode(newMode: OperationalMode): InOut[A] = {
    if (newMode==mode) this
    else this.copy(mode=newMode)
  }
  
  /**
   * Update the value of the monitor point
   * 
   * @param newValue: The new value of the monitor point
   */
  def updateValue[B >: A](newValue: Option[B]): InOut[A] = update(newValue,validity)
  
  /**
   * Update the value and validity of the monitor point
   * 
   * @param newValue: The new value of the monitor point
   * @param valid: the new validity
   * @return A new InOut with updated value and validity
   */
  def update[B >: A](newValue: Option[B], valid: Validity): InOut[A] = {
    if (newValue==actualValue.value && valid==validity) this 
    else InOut(newValue,id,refreshRate,mode,valid,iasType)
  }
  
  /**
   * Update the validity of the monitor point
   * 
   * @param valid: The new validity of the monitor point
   */
  def updateValidity(valid: Validity): InOut[A] = {
    if (valid==validity) this
    else this.copy(validity=valid)
  }
  
  /**
   * Update the value of this IASIO with the IASValue received from the
   * BSDB
   * 
   * TODO: the validity must also be updated when it will be sent 
   *       by the plugin (@see Issue #19 on github)
   */
  def update[B >: IASValue[A]](iasValue: B): InOut[A] = {
    val v = iasValue.asInstanceOf[IASValue[A]]
    require(Option(iasValue).isDefined,"Cannot update from a undefined IASIO")
    require(v.id==this.id.id,"Identifier mismatch: received "+v.id+", expected "+this.id.id)
    assert(v.valueType==this.iasType)
    
    updateValue(Option[A](v.value)).updateMode(v.mode).updateValidity(v.iasValidity)
  }
}

/** 
 *  InOut companion object
 */
object InOut {
  
  /**
   * The min possible value for the refresh rate
   * If it is too short the value will be invalid most of the time; if too 
   * long it is not possible to understand if it has been properly refreshed or
   * the source is stuck/dead.
   */
  val MinRefreshRate = 50;

  /**
   * Check if the passed value is of the proper type
   * 
   * @param value: The value to check they type against the iasType
   * @param iasType: The IAS type
   */
  def checkType[T](value: T, iasType: IASTypes): Boolean = {
    if (value==None) true
    else iasType match {
      case IASTypes.LONG => value.isInstanceOf[Long]
      case IASTypes.INT => value.isInstanceOf[Int]
      case IASTypes.SHORT => value.isInstanceOf[Short]
      case IASTypes.BYTE => value.isInstanceOf[Byte]
      case IASTypes.DOUBLE => value.isInstanceOf[Double]
      case IASTypes.FLOAT => value.isInstanceOf[Float]
      case IASTypes.BOOLEAN =>value.isInstanceOf[Boolean]
      case IASTypes.CHAR => value.isInstanceOf[Char]
      case IASTypes.STRING => value.isInstanceOf[String]
      case IASTypes.ALARM =>value.isInstanceOf[AlarmSample]
      case _ => false
    }
  }
  
  /**
   * Build a InOut with no initial value neither a mode.
   * 
   * Such a IASIO is useful when it is expected but has not yet been sent
   * by the BSDB or a ASCE: we know that it exists but we do not know its 
   * initial values.
   * 
   * @param id the identifier
   * @param refreshRate the refresh rate of this IASIO
   * @param iasType the type of the value of the IASIO
   * @return a InOut initially empty
   */
  def apply[T](id: Identifier,
    refreshRate: Int,
    iasType: IASTypes): InOut[T] = {
    InOut[T](None,id,refreshRate,OperationalMode.UNKNOWN,UNRELIABLE,iasType)
  }
  
  /**
   * Build a IASIO from a IASVAlue received from the BDSB.
   * 
   * @param iasValue the value received from the BSDB
   * @param refreshRate the refresh rate of this IASIO
   * @param parentIdentifier the parent identifier
   * @return the IASIO generated from the passed IASValue
   */
  def apply[T](iasValue: IASValue[T], refreshRate: Int) = {
    val id = Identifier(iasValue.runningId)
    val value = Option[T](iasValue.value)
    // TODO: the validity should be set by the plugins (or other DASUs) i.e.
    //       be part of the IASValue. Setting a value as Reliable by default does 
    //       not work (@see Issue #19 on github)
    val validity = Validity(iasValue.iasValidity)
    val mode = iasValue.mode
    val iasType = iasValue.valueType
    new InOut[T](value, id,refreshRate ,mode, validity,iasType)
  }

}