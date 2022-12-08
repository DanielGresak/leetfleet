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
import lf.message.WebPortalMsg;
import lf.model.Fleet;

import java.util.*;

import com.fasterxml.jackson.annotation.JsonProperty;

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
 * The Registry is used in LeetFleet for routing Vehcile events to the correct
 * FleetManager.  It is lightweight, doesn't store any message state and should
 * be "restartable" with minimum impact.
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

  // COMMENT COMMENT COMMENT
  public final static class ListFleetMgrRefs implements Message, LFSerialisable {
    public final String fleetId;
    public final ActorRef<VehicleEventMsg.Message> vehicleEventHandlerRef;

    public ListFleetMgrRefs(String fleetId, ActorRef<VehicleEventMsg.Message> vehicleEventHandlerRef) {
      this.fleetId = fleetId;
      this.vehicleEventHandlerRef = vehicleEventHandlerRef;
    }
  }

 /**
  * Request for a list the registered fleet managers in JSON format.
  */
  public final static class ListFleetMgrsJson implements Message, LFSerialisable {
    public final ActorRef<WebPortalMsg.FleetListToWebP> portalRef;

    public ListFleetMgrsJson(@JsonProperty("portalRef") ActorRef<WebPortalMsg.FleetListToWebP> portalRef) {
      this.portalRef = portalRef;
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

  // Track which id's map to which 'FleetManager Actor References' (as the manager registrations can arrive in any order).
  private static HashMap<Long, ActorRef<FleetManagerMsg.Message>> registry
    = new HashMap<Long, ActorRef<FleetManagerMsg.Message>>();

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
        // .onMessage(RegisterFleetManager.class, this::onRegFleetManager)
        // .onMessage(DeRegisterManager.class, this::onDeRegFleetManager)
        .onMessage(ListFleetMgrRefs.class, this::onListFleetMgrRefs)
        .onMessage(ListFleetMgrsJson.class, this::onListFleetMgrsJson)
        .onMessage(ListingResponse.class, this::onListing)
        .build();
  }

  // From FleetManager

  // From VehicleEvent

  private Behavior<Message> onListFleetMgrRefs(ListFleetMgrRefs message) {
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
      message.vehicleEventHandlerRef.tell(new VehicleEventMsg.FleetMgrRefList(Collections.singletonList(registry.get(fleetId)), getContext().getSelf()));
    }
    else {
      message.vehicleEventHandlerRef.tell(new VehicleEventMsg.FleetMgrRefList(registry.values(), getContext().getSelf()));
    }

    return this;
  }

  private Behavior<Message> onListFleetMgrsJson(ListFleetMgrsJson message) {
    // Spoof method. In reality we would have modelled fleet managers. In this
    // toy system the four managers are just hard coded.  We could have put this
    // method anywhere - I put it here as "it's the closest thing to where fleet
    // managers are actually stored" in our demo system.
    ArrayList<Fleet> fleets = new ArrayList<Fleet>();
    fleets.add(new Fleet("CarelessFleet", "1"));
    fleets.add(new Fleet("FastidiousFleet", "2"));
    fleets.add(new Fleet("FleetleesFleet", "3"));
    fleets.add(new Fleet("ParanoidFleet", "4"));
    message.portalRef.tell(new WebPortalMsg.FleetListToWebP(fleets));

    return this;
  }

  // From Receptionist

  private Behavior<Message> onListing(ListingResponse msg) {
    getContext().getLog().info("Receptionist Notification - Fleet Manager Created:");

    Set<ActorRef<FleetManagerMsg.Message>> fleetManagerServiceInstances
      = msg.listing.getServiceInstances(FleetManagerMsg.fleetManagerServiceKey);

    // We need to loop over the set of fleetmanager references received from the
    // receptionist and carefully compare the contents:
    // -> If new actors are present they have been created - we need to add them
    //    and assign an ID to them.
    fleetManagerServiceInstances.forEach(
      fleetManagerRef -> {
        if (!registry.values().contains(fleetManagerRef)) {
          // This is a new FleetManager reference - we want to add it.
          long newId = SEED_ID++;
          registry.put(newId, fleetManagerRef);
          getContext().getLog().info("\t(fleet manager ref added to registry cache)");

          // We inform the FleetManager that registration was successful
          fleetManagerRef.tell(new FleetManagerMsg.RegistrationSuccess(newId, getContext().getSelf()));
        }
      });

    // The Registry should now contain all the latest FleetManager refs. But what
    // if some have been removed?
    // -> If actors currently present in the registry are missing from the received
    //    Set that implies they have been removed - we need to remove them too!
    ArrayList<Long> deadFleetManagerKeys = new ArrayList<Long>();
    for (Map.Entry<Long, ActorRef<FleetManagerMsg.Message>> registryEntry : registry.entrySet()) {
        if (!fleetManagerServiceInstances.contains(registryEntry.getValue())) {
          // This is an old FleetManager reference - we want to remove it.
          // Cannot modify a map while iterating over it.
          deadFleetManagerKeys.add(registryEntry.getKey());
        }
    }
    for (Long key : deadFleetManagerKeys) {
      registry.remove(key);
      getContext().getLog().info("\t(fleet manager ref removed from registry cache)");
      // The is no actor to inform that "FleetManager Has been De-registered"
      // as the actor is already gone.
    }

    return Behaviors.same();
  }

    // TODO STORE message.fleetManName!!
}