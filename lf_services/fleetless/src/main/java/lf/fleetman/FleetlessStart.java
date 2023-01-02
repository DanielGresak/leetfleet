package lf.fleetman;

import akka.actor.typed.ActorSystem;
import lf.actor.FleetlessGuardian;
import lf.core.LeetFServiceStart;
import lf.message.LeetFServiceGuardianMsg;

import com.typesafe.config.Config;

public class FleetlessStart extends LeetFServiceStart {

  public static void main(String[] args) {
    akkaHostname = "fleetless"; // Sensible defaults
    akkaPort     = 2554;

    configFromArgs(args);

    Config config = buildOverrideAkkaConfig();

    // An ActorSystem is the intial entry point into Akka.
    // Usually only one ActorSystem is created per application.
    // An ActorSystem has a name and a guardian actor.
    // The bootstrap of your application is typically done within the guardian actor.

    //#actor-system
    final ActorSystem<LeetFServiceGuardianMsg.BootStrap> fleetlessGuardian
                  = ActorSystem.create(FleetlessGuardian.create(), "leet-fleet", config);
    //#actor-system

    //#main-send-messages
    fleetlessGuardian.tell(new LeetFServiceGuardianMsg.BootStrap("Leet-Fleet"));
    //#main-send-messages

    gracefulInteractiveTermination(fleetlessGuardian);

  }

}