package com.softwaremill.bootzooka.infrastructure

import cats.effect.kernel.Sync
import com.softwaremill.bootzooka.util.{Id, PasswordHash}
import com.softwaremill.tagging._
import com.typesafe.scalalogging.StrictLogging
import doobie.util.log.{ExecFailure, LogEvent, ProcessingFailure, Success}

import scala.concurrent.duration._

/** Import the members of this object when defining SQL queries using doobie.
  */
object Doobie
    extends doobie.Aliases
    with doobie.hi.Modules
    with doobie.free.Modules
    with doobie.free.Types
    with doobie.postgres.Instances
    with doobie.util.meta.LegacyInstantMetaInstance
    with doobie.free.Instances
    with doobie.syntax.AllSyntax
    with StrictLogging {

  implicit def idMeta: Meta[Id] = implicitly[Meta[String]].asInstanceOf[Meta[Id]]

  // there's no TypeTag for Id +
  // can't define a generic encoder because of https://stackoverflow.com/questions/48174799/decoding-case-class-w-tagged-type
  implicit def taggedIdMeta[U]: Meta[Id @@ U] = implicitly[Meta[String]].asInstanceOf[Meta[Id @@ U]]

  implicit def taggedStringMeta[U]: Meta[String @@ U] =
    implicitly[Meta[String]].asInstanceOf[Meta[String @@ U]]

  implicit val passwordHashMeta: Meta[String @@ PasswordHash] =
    implicitly[Meta[String]].asInstanceOf[Meta[String @@ PasswordHash]]

  private val SlowThreshold = 200.millis

  /** Logs the SQL queries which are slow or end up in an exception.
    */
  implicit def doobieLogHandler[M[_]: Sync]: LogHandler[M] = new LogHandler[M] {
    override def run(logEvent: LogEvent): M[Unit] = Sync[M].delay(
      logEvent match {
        case Success(sql, _, _, exec, processing) =>
          if (exec > SlowThreshold || processing > SlowThreshold) {
            logger.warn(s"Slow query (execution: $exec, processing: $processing): $sql")
          }

        case ProcessingFailure(sql, args, _, exec, processing, failure) =>
          logger.error(s"Processing failure (execution: $exec, processing: $processing): $sql | args: $args", failure)

        case ExecFailure(sql, args, _, exec, failure) =>
          logger.error(s"Execution failure (execution: $exec): $sql | args: $args", failure)
      }
    )
  }
}
