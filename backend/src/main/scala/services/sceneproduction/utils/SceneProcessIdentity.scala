package services.sceneproduction.utils
import java.time.{Instant,Duration}

// Windows exposes process birth time but may omit command line and arguments.
private[sceneproduction] object SceneProcessIdentity:
  def matches(recordedStart: Option[Instant],observedStart: Option[Instant],launchWrittenAt: Instant,commandLine: Option[String],id: String): Boolean =
    recordedStart match
      case Some(expected) => observedStart.contains(expected)
      case None =>
        // Existing launch records predate birth-time recording. Their timestamp was
        // written immediately after ProcessBuilder.start; reject a reused PID.
        observedStart.exists { actual =>
          val delta=Duration.between(actual,launchWrittenAt).toMillis
          delta>=0 && delta<=10000
        } || commandLine.exists(_.contains(id))
