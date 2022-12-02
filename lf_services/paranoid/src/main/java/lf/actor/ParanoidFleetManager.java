package lf.actor;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.receptionist.Receptionist;
import lf.message.FleetManager;
import lf.message.FleetManager.Message;
import lf.message.FleetManager.RegistrationSuccess;

/**
 * A Fleet Manager. This time for the Notional Paranoid Fleet. Could suit any abstraction.
 */
public class ParanoidFleetManager extends AbstractBehavior<Message> {

    // ENCAPSULATION:
    public long MANAGER_ID = 0;
    public ActorRef<Registry.Message> REGISTRY_REF = null;

    // CREATE THIS ACTOR
    public static Behavior<Message> create() {
        return Behaviors.setup(
            // Register this actor with the receptionist
            context -> {
                context
                    .getSystem()
                    .receptionist()
                    .tell(Receptionist.register(FleetManager.fleetManagerServiceKey, context.getSelf()));

                return Behaviors.setup(ParanoidFleetManager::new);
            }
        );
    }

    // ADD TO CONTEXT
    protected ParanoidFleetManager(ActorContext<Message> context) {
        super(context);
        // send a message to the registry to register!!!!  FFS

        // akka://my-sys@host.example.com:5678/user/service-b
        // registry.tell(new Registry.RegisterFleetManager(getContext().getSelf()));
    }

    // =========================================================================

    // MESSAGE HANDLING:
    @Override
    public Receive<Message> createReceive() {
        return newReceiveBuilder()
                .onMessage(RegistrationSuccess.class, this::onRegistrationSuccess)
                .build();
    }

    private Behavior<Message> onRegistrationSuccess(RegistrationSuccess message) {
        // Store the unique id assigned to this FleetManager. We'll need it if
        // we want to 'DeRegister' on shutdown...
        MANAGER_ID   = message.mgrId;
        REGISTRY_REF = message.registryRef;
        getContext().getLog().info("REGISTRY HAS CONFIRMED REGISTRATION !!!");
        return this;
    }

}