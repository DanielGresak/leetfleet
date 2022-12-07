package lf.actor;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.ServiceKey;
import lf.message.FleetManagerMsg;
import lf.message.LFSerialisable;
import lf.message.VehicleEventMsg;

import java.util.*;

// READ THESE FIRST:
// What is an Actor?
// https://doc.akka.io/docs/akka/current/general/actors.html#what-is-an-actor-
// https://doc.akka.io/docs/akka/current/typed/actors.html
//
// NOTE: Actors have an explicit lifecycle, they are not automatically destroyed
// when no longer referenced. After having created one, it is YOUR responsibility
// to make sure that it will eventually be terminated as well
// Messages are sent to an "actor Reference" and behind this façade there is a
// Behavior that receives the message and acts upon it.

/**
 *
 */
public class Registry extends AbstractBehavior<Registry.Message> {

  // Create a ServiceKey so we can find the Registry using the Receptioninst
  // The API of the receptionist is based on actor messages.
  public static final ServiceKey<Registry.Message> registrySK
      = ServiceKey.create(Registry.Message.class, "registrySK");

  // MESSAGES:
  //
  // It is a good practice to put an actor’s associated messages as static
  // classes in the AbstractBehavior’s class. This makes it easier to understand
  // what type of messages the actor expects and handles.
  //
  // Typically, an actor handles more than one specific message type and then
  // there
  // is one common interface that all messages that the actor can handle
  // implements.
  public interface Message {
  }

  // Messages *are* the Actor’s public API, it is a good practice to define
  // messages with good
  // names and rich semantic and domain specific meaning, even if they just wrap
  // your data type.
  // This will make it easier to use, understand and debug actor-based system

  public final static class RegisterFleetManager implements Message, LFSerialisable {
    public final ActorRef<FleetManagerMsg.Message> fleetManRef;

    public RegisterFleetManager(ActorRef<FleetManagerMsg.Message> fleetManRef) {
      this.fleetManRef = fleetManRef;
    }
  }

  /* This message does not warrant a response - so no ActorRef stored */
  public final static class DeRegisterManager implements Message, LFSerialisable {
    public final long idToBeDeRegistered;

    public DeRegisterManager(long idToBeDeRegistered) {
      this.idToBeDeRegistered = idToBeDeRegistered;
    }
  }

  // COMMENT COMMENT COMMENT
  public final static class ListFleetManagers implements Message, LFSerialisable {
    public final String fleetId;
    public final ActorRef<VehicleEventMsg.Message> vehicleEventHandlerRef;

    public ListFleetManagers(String fleetId, ActorRef<VehicleEventMsg.Message> vehicleEventHandlerRef) {
      this.fleetId = fleetId;
      this.vehicleEventHandlerRef = vehicleEventHandlerRef;
    }
  }

  /**
   * Message to handle Listing Response from Receptionist.
   *
   * NOTE: We need to emply a 'messageAdaptor' to convert the message we recieve
   * from the Receptioninst to the one define here that we can understand.
   */
  private static class ListingResponse implements Message, LFSerialisable {
    final Receptionist.Listing listing;

    private ListingResponse(Receptionist.Listing listing) {
      this.listing = listing;
    }
  }

  // ENCAPSULATION:

  private static long SEED_ID = 10000;

  // Track which id's map to which 'ClientInfos' (as the responses
  // can arrive in any order).
  private static HashMap<Long, ActorRef<FleetManagerMsg.Message>> registry = new HashMap<Long, ActorRef<FleetManagerMsg.Message>>();

  // We need an 'adaptor' - to convert the Receptionist Listing to one we
  // understand!!
  private final ActorRef<Receptionist.Listing> listingResponseAdapter;

  // CREATE THIS ACTOR
  public static Behavior<Message> create() {
    return Behaviors.setup(
        // Register this actor with the receptionist
        context -> {
          context
              .getSystem()
              .receptionist()
              .tell(Receptionist.register(registrySK, context.getSelf()));

          //TODO FIX FIX FIX - THINK FOLLOWING IS CORRECT - TEST IT TEST IT TEST IT
          //return new Registry(context);
          return Behaviors.setup(Registry::new);
        });
  }

  // ADD TO CONTEXT
  private Registry(ActorContext<Message> context) {
    super(context);

    this.listingResponseAdapter = context.messageAdapter(Receptionist.Listing.class, ListingResponse::new);

    // Subscribe for FleetManager list updates!
    context
        .getSystem()
        .receptionist()
        .tell(
            Receptionist.subscribe(
                FleetManagerMsg.fleetManagerServiceKey, listingResponseAdapter));
  }

  // =========================================================================

  // MESSAGE HANDLING:
  @Override
  public Receive<Message> createReceive() {
    return newReceiveBuilder()
        .onMessage(RegisterFleetManager.class, this::onRegFleetManager)
        .onMessage(DeRegisterManager.class, this::onDeRegFleetManager)
        .onMessage(ListFleetManagers.class, this::onListFleetManagers)
        .onMessage(ListingResponse.class, this::onListing)
        .build();
  }

  // From FleetManager

  // The type of the messages handled by this behavior is declared to be of class
  // message
  private Behavior<Message> onRegFleetManager(RegisterFleetManager message) {
    // The fleet manager has registered.
    long new_fleet_id = SEED_ID++;
    registry.put(new_fleet_id, message.fleetManRef);

    // We inform the FleetManager that registration was successful
    message.fleetManRef.tell(new FleetManagerMsg.RegistrationSuccess(new_fleet_id, getContext().getSelf()));
    return this;
  }

  // DeRegistration is part of orderly shutdown. We don't confirm.
  private Behavior<Message> onDeRegFleetManager(DeRegisterManager message) {
    registry.remove(message.idToBeDeRegistered);
    return this;
  }

  // From VehicleEvent

  private Behavior<Message> onListFleetManagers(ListFleetManagers message) {
    // If valid fleetId
    boolean validFleetId = false;
    long fleetId         = 0;
    try {
        fleetId = Long.parseLong(message.fleetId);
        validFleetId = true;
    } catch (NumberFormatException nfe) {
    }

    if (validFleetId) {
      // We have to return a Collection - use the singletonList convenience...
      message.vehicleEventHandlerRef.tell(new VehicleEventMsg.FleetManagerList(Collections.singletonList(registry.get(fleetId)), getContext().getSelf()));
    }
    else {
      message.vehicleEventHandlerRef.tell(new VehicleEventMsg.FleetManagerList(registry.values(), getContext().getSelf()));
    }

    return this;
  }

  // From Receptionist

  private Behavior<Message> onListing(ListingResponse msg) {
    getContext().getLog().info("Receptionist Notification - Fleet Manager Created:");
    registry = new HashMap<Long, ActorRef<FleetManagerMsg.Message>>();
    msg.listing.getServiceInstances(FleetManagerMsg.fleetManagerServiceKey)
        .forEach(
            fleetManagerRef -> {
              // Refresh entire registry every time?
              registry.put(SEED_ID++, fleetManagerRef);
              getContext().getLog().info("\t(fleet manager ref added to registry cache)");
            });
    return Behaviors.same();
  }

}