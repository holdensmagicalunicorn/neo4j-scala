package org.neo4j.scala.util

import scalax.rules.scalasig._
import collection.mutable.ArrayBuffer

/**
 * helper class to store Class object
 */
case class JavaType(c: Class[_])

/**
 * Case Class deserializing object
 */
object CaseClassDeserializer {

  /**
   * convenience method using class manifest
   * use it like <code>val test = deserialize[Test](myMap)<code>
   */
  def deserialize[T: ClassManifest](m: Map[String, AnyRef]): T = {
    val cm = implicitly[ClassManifest[T]]
    deserialize(m, JavaType(cm.erasure)).asInstanceOf[T]
  }

  /**Creates a case class instance from parameter map
   *
   * @param m Map[String, AnyRef] map of parameter name an parameter type
   * @param javaTypeTarget JavaType case class class to create
   */
  def deserialize(m: Map[String, AnyRef], javaTypeTarget: JavaType) = {
    require(javaTypeTarget.c.getConstructors.length == 1, "Case classes must only have one constructor.")
    val constructor = javaTypeTarget.c.getConstructors.head
    val params = CaseClassSigParser.parse(javaTypeTarget.c)

    val values = new ArrayBuffer[AnyRef]
    for ((paramName, paramType) <- params) {
      val field = m.getOrElse(paramName, throw new RuntimeException("Field: " + paramName + " of type: " + paramType.c + " not found"))

      /**
       * if the value is directly assignable: use it
       * otherwise try to create an instnace using der String Constructor
       */
      if (field.getClass.isAssignableFrom(paramType.c))
        values += field
      else {
        val paramCtor = paramType.c.getConstructor(classOf[String])
        val value = paramCtor.newInstance(field).asInstanceOf[AnyRef]
        values += value
      }
    }
    constructor.newInstance(values.toArray: _*).asInstanceOf[AnyRef]
  }

  /**
   * creates a map from case class parameter
   * @param o AnyRef case class instance
   */
  def serialize(o: AnyRef): Map[String, AnyRef] = {
    val methods = o.getClass.getDeclaredMethods
      .filter {
      _.getParameterTypes.isEmpty
    }
      .map {
      m => m.getName -> m
    }.toMap
    val params = CaseClassSigParser.parse(o.getClass)
    val l = for ((paramName, _) <- params; value = methods.get(paramName).get.invoke(o)) yield (paramName, value)
    l.toMap
  }
}

class MissingPickledSig(clazz: Class[_]) extends Error("Failed to parse pickled Scala signature from: %s".format(clazz))

class MissingExpectedType(clazz: Class[_]) extends Error(
  "Parsed pickled Scala signature, but no expected type found: %s"
    .format(clazz)
)

object CaseClassSigParser {
  val SCALA_SIG = "ScalaSig"
  val SCALA_SIG_ANNOTATION = "Lscala/reflect/ScalaSignature;"
  val BYTES_VALUE = "bytes"

  protected def parseScalaSig[A](clazz: Class[A]): Option[ScalaSig] = {
    val firstPass = ScalaSigParser.parse(clazz)
    firstPass match {
      case Some(x) => {
        Some(x)
      }
      case None if clazz.getName.endsWith("$") => {
        val clayy = Class.forName(clazz.getName.replaceFirst("\\$$", ""))
        val secondPass = ScalaSigParser.parse(clayy)
        secondPass
      }
      case x => x
    }
  }

  protected def findSym[A](clazz: Class[A]) = {
    val pss = parseScalaSig(clazz)
    pss match {
      case Some(x) => {
        val topLevelClasses = x.topLevelClasses
        topLevelClasses.headOption match {
          case Some(tlc) => {
            tlc
          }
          case None => {
            val topLevelObjects = x.topLevelObjects
            topLevelObjects.headOption match {
              case Some(tlo) => {
                tlo
              }
              case _ => throw new MissingExpectedType(clazz)
            }
          }
        }
      }
      case None => throw new MissingPickledSig(clazz)
    }
  }

  def parse[A](clazz: Class[A]) = {
    findSym(clazz).children
      .filter(c => c.isCaseAccessor && !c.isPrivate)
      .map(_.asInstanceOf[MethodSymbol])
      .zipWithIndex
      .flatMap {
      case (ms, idx) => {
        ms.infoType match {
          case NullaryMethodType(t: TypeRefType) => Some(ms.name -> typeRef2JavaType(t))
          case _ => None
        }
      }
    }
  }

  protected def typeRef2JavaType(ref: TypeRefType): JavaType = {
    try {
      JavaType(loadClass(ref.symbol.path))
    } catch {
      case e: Throwable => {
        e.printStackTrace()
        null
      }
    }
  }

  protected def loadClass(path: String) = path match {
    case "scala.Predef.Map" => classOf[Map[_, _]]
    case "scala.Predef.Set" => classOf[Set[_]]
    case "scala.Predef.String" => classOf[String]
    case "scala.package.List" => classOf[List[_]]
    case "scala.package.Seq" => classOf[Seq[_]]
    case "scala.package.Sequence" => classOf[Seq[_]]
    case "scala.package.Collection" => classOf[Seq[_]]
    case "scala.package.IndexedSeq" => classOf[IndexedSeq[_]]
    case "scala.package.RandomAccessSeq" => classOf[IndexedSeq[_]]
    case "scala.package.Iterable" => classOf[Iterable[_]]
    case "scala.package.Iterator" => classOf[Iterator[_]]
    case "scala.package.Vector" => classOf[Vector[_]]
    case "scala.package.BigDecimal" => classOf[BigDecimal]
    case "scala.package.BigInt" => classOf[BigInt]
    case "scala.package.Integer" => classOf[java.lang.Integer]
    case "scala.package.Character" => classOf[java.lang.Character]
    case "scala.Long" => classOf[java.lang.Long]
    case "scala.Int" => classOf[java.lang.Integer]
    case "scala.Boolean" => classOf[java.lang.Boolean]
    case "scala.Short" => classOf[java.lang.Short]
    case "scala.Byte" => classOf[java.lang.Byte]
    case "scala.Float" => classOf[java.lang.Float]
    case "scala.Double" => classOf[java.lang.Double]
    case "scala.Char" => classOf[java.lang.Character]
    case "scala.Any" => classOf[Any]
    case "scala.AnyRef" => classOf[AnyRef]
    case name => Class.forName(name)
  }
}

