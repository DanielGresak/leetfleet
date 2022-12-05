package lf.actor;

import java.util.HashMap;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.receptionist.Receptionist;
import lf.core.VehicleIdRange;
import lf.message.FleetManagerMsg;
import lf.message.FleetManagerMsg.Message;
import lf.message.FleetManagerMsg.ProcessVehicleUpdate;
import lf.message.FleetManagerMsg.RegistrationSuccess;
import lf.model.Vehicle;

/**
 * A Fleet Manager. This time for the Notional Fleetless Fleet. Could suit any abstraction.
 */
public class FleetlessFleetManager extends AbstractBehavior<Message> {

    // ENCAPSULATION:
    public long MANAGER_ID = 3;
    private VehicleIdRange fleetlessFleetIdRange = new VehicleIdRange(5000, 7499);

    public ActorRef<Registry.Message> REGISTRY_REF = null;

    // Track the VehicleTwin actors we have "live" (active actor refs in the
    // cluster).
    // ?!IF!? we had gotten the java WoT working - this may well have been WoT
    // "consumed thing" that was ALSO an akka actor. That... would have been sweet.
    private static HashMap<Long, ActorRef<VehicleTwin.Message>> vehicles = new HashMap<Long, ActorRef<VehicleTwin.Message>>();

    // CREATE THIS ACTOR
    public static Behavior<Message> create() {
        return Behaviors.setup(
            // Register this actor with the receptionist
            context -> {
                context
                    .getSystem()
                    .receptionist()
                    .tell(Receptionist.register(FleetManagerMsg.fleetManagerServiceKey, context.getSelf()));

                return Behaviors.setup(FleetlessFleetManager::new);
            }
        );
    }

    // ADD TO CONTEXT
    protected FleetlessFleetManager(ActorContext<Message> context) {
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
                .onMessage(ProcessVehicleUpdate.class, this::onProcessVehicleUpdate)
                .build();
    }

    private Behavior<Message> onRegistrationSuccess(RegistrationSuccess message) {
        // Store the unique id assigned to this FleetManager. We'll need it if
        // we want to 'DeRegister' on shutdown...
        MANAGER_ID   = message.mgrId;
        REGISTRY_REF = message.registryRef;
        getContext().getLog().info("FleetManager Registration Confirmed.");
        return this;
    }

    private Behavior<Message> onProcessVehicleUpdate(ProcessVehicleUpdate message) {
        // Each VehicleId is in the format 'WoT-ID-Mfr-VIN-nnnn' in our Toy system
        // We extract the 'nnnn' (id) part to see if this vehicle belongs to this
        // fleet manager:
        Vehicle vehicle = message.vehicle;
        long vehicleIdLong = vehicle.getVehicleIdLong();

        if (vehicleIdLong != 0) {
            if (fleetlessFleetIdRange.contains(vehicleIdLong)) {
                getContext().getLog().info("Vehicle Event for CareleesFleet received.");

                // This might be the first communication for this vehicle. It
                // might not. Just stamp it with this fleetId every time.
                vehicle.setFleetManager(Long.toString(MANAGER_ID));
                getContext().getLog().info("\tVehicle Manager ('ID') set to -> " + vehicle.getFleetManager());

                ActorRef<VehicleTwin.Message> vehicleTwinRef;

                // First - if the VehicleTwin for this vehicle doesn't exist, we
                // must create an actor for it:
                if (!vehicles.keySet().contains(vehicleIdLong)) {
                    // Create an (anonymous) VehicleTwin actor to represent this vehicle on the
                    // actor system
                    vehicleTwinRef = getContext()
                            .spawnAnonymous(VehicleTwin.create(vehicle.getVehicleId()));  // 'anonymous' actor
                    vehicles.put(vehicleIdLong, vehicleTwinRef);
                }
                else {
                    vehicleTwinRef = vehicles.get(vehicleIdLong);
                }

                // Update the VehicleTwin with the 'vehicle' pojo we have been
                // sent
                vehicleTwinRef.tell(new VehicleTwin.Update(vehicle));

                // We message the VehicleEvent handler immediately to say we're
                // done. There's no confirmation etc.. Worst case - we lose one
                // message and the client reporting is one transaction out of date.
                // A real system might take a different approach here, depending
                // on the designers goals.
                message.vehicleEventRef.tell(new VehicleEvent.EventComplete(vehicle));

            } else {
                getContext().getLog().info(
                        "Vehicle Event for non-fleet vehicle received (" + String.valueOf(vehicleIdLong) + "). Ignoring.");
            }
        }

        return this;
    }

}