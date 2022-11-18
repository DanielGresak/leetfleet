// server.js
// Required steps to create a servient for creating a thing
const Servient = require('@node-wot/core').Servient;
const HttpServer = require('@node-wot/binding-http').HttpServer;

const servient = new Servient();
servient.addServer(new HttpServer());

servient.start().then((WoT) => {
    let allAvailableResources;
    let totalMileage;
    let mileageServiceInterval;
    let maintenanceNeeded;
    // Initialise tyre pressure, oil level, and door status
    let tyrePressure = getRandomInt(20, 35); // [PSI]
    let oilLevel = getRandomInt(0,100); // [%]
    let doorStatus = LOCKED;
    WoT.produce({
        title: "Smart-Vehicle",
        description: `A smart vehicle that connects with our fleet manager`,
        support: "https://github.com/eclipse/thingweb.node-wot/",
        properties: {
            allAvailableResources: {
                type: "object",
                description: `Current level of all available resources given as an integer percentage for each particular resource.
    The data is obtained from sensors but can be set manually via the availableResourceLevel property in case the sensors are broken.`,
                readOnly: true,
                observable: false,
                properties: {
                    oilLevel: {
                        type: "integer",
                        minimum: 0,
                        maximum: 100,
                    },
                    tyrePressure: {
                        type: "integer",
                        minimum: 0,
                        maximum: 100,
                    },
                },
            },
            availableResourceLevel: {
                type: "number",
                description: `Current level of a particular resource. Requires resource id variable as uriVariables.
    The property can also be overridden, which also requires resource id as uriVariables.`,
                uriVariables: {
                    id: {
                        type: "string",
                        enum: ["oilLevel", "tyrePressure"],
                    },
                },
            },
            totalMileage: {
                type: "integer",
                description: `The total mileage of the vehicle.`,
                minimum: 0,
            },
            mileageServiceInterval: {
                type: "integer",
                description: `Mileage counter for service intervals.`,
                minimum: 0,
            },
            doorStatus: {
                title: "Door status",
                type: "string",
                enum: ["LOCKED", "UNLOCKED"]
            },
            maintenanceNeeded: {
                type: "boolean",
                description: `Indicates when maintenance is needed. The property is observable. Automatically set to true if oil or tyre pressure is too low.`,
                observable: true,
            },
        },
        actions: {
            lockDoor: {
                description: `Lock the car door`,
                output: { type: "string" },
            },
            unlockDoor: {
                description: `Unlock the car door`,
                output: { type: "string" },
            },
        },
        events: {
            lowOnOil: {
                description: `Low on oil.`,
                data: {
                    type: "string",
                },
            },
            lowTyrePressure: {
                description: `Low tyre pressure.`,
                data: {
                    type: "string",
                },
            },
        },
    })
        .then((thing) => {
            // Initialize the property values
            allAvailableResources = {
                oil: readFromSensor("oilLevel"),
                tyrePressure: readFromSensor("tyrePressure")
            };
            maintenanceNeeded = false;
            thing.setPropertyReadHandler("allAvailableResources", async () => allAvailableResources);
            thing.setPropertyReadHandler("availableResourceLevel", async () => availableResourceLevel);
            thing.setPropertyReadHandler("maintenanceNeeded", async () => maintenanceNeeded);
            thing.setPropertyReadHandler("totalMileage", async () => totalMileage);
            thing.setPropertyReadHandler("mileageServiceInterval", async () => mileageServiceInterval);
            thing.setPropertyReadHandler("doorStatus", async () => doorStatus);
            // Override a write handler for mileageServiceInterval property,
            // raising maintenanceNeeded flag when the interval exceeds 30,000 km
            thing.setPropertyWriteHandler("mileageServiceInterval", async (val) => {
                mileageServiceInterval = await val.value();
                if (mileageServiceInterval > 30000) {
                    maintenanceNeeded = true;
                    thing.emitPropertyChange("maintenanceNeeded");
                    // Notify a "maintainer" when the value has changed
                    // (the notify function here simply logs a message to the console)
                    notify(
                        "admin@leetfleet.com",
                        `maintenanceNeeded property has changed, new value is: ${maintenanceNeeded}`
                    );
                }
            });
            // Now initialize the mileage service interval property
            mileageServiceInterval = readMilometerServiceInterval("mileageServiceInterval");
            // Override a write handler for availableResourceLevel property,
            // utilizing the uriVariables properly
            thing.setPropertyWriteHandler("availableResourceLevel", async (val, options) => {
                // Check if uriVariables are provided
                if (options && typeof options === "object" && "uriVariables" in options) {
                    const uriVariables = options.uriVariables;
                    if ("id" in uriVariables) {
                        const id = uriVariables.id;
                        allAvailableResources[id] = await val.value();
                        return;
                    }
                }
                throw Error("Please specify id variable as uriVariables.");
            });
            // Override a read handler for availableResourceLevel property,
            // utilizing the uriVariables properly
            thing.setPropertyReadHandler("availableResourceLevel", async (options) => {
                // Check if uriVariables are provided
                if (options && typeof options === "object" && "uriVariables" in options) {
                    const uriVariables = options.uriVariables;
                    if ("id" in uriVariables) {
                        const id = uriVariables.id;
                        return allAvailableResources[id];
                    }
                }
                throw Error("Please specify id variable as uriVariables.");
            });
            // Set up a handler for lockDoor action
            thing.setActionHandler("lockDoor", async () => {
                doorStatus = LOCKED;
                return `Door is locked!`;
            });
            // Set up a handler for unlockDoor action
            thing.setActionHandler("unlockDoor", async () => {
                doorStatus = UNLOCKED;
                return `Door is unlocked!`;
            });

            // Finally expose the thing
            thing.expose().then(() => {
                console.info(`${thing.getThingDescription().title} ready`);
            });
            console.log(`Produced ${thing.getThingDescription().title}`);
        })
        .catch((e) => {
            console.log(e);
        });
    function readFromSensor(sensorType) {
        if (sensorType == "tyrePressure") {
            // Decrease pressure between 1 and 2 PSI
            tyrePressure -= getRandomInt(0,2);
        } else if (sensorType == "oilLevel") {
            // Decrease oil level between 1 and 10%
            oilLevel -= getRandomInt(0,10);
        }
    }
    function notify(subscribers, msg) {
        // Actual implementation of notifying subscribers with a message can go here
        console.log(msg);
    } 
    function readMilometerServiceInterval() {
        return mileageServiceInterval;
    }   
    function readMilometer() {
        // Emulate mileage by increasing it randomly between 0 and 200 km
        mileageIncrease = getRandomInt(0, 200);
        totalMileage += mileageIncrease;
        mileageServiceInterval += mileageIncrease;
        return totalMileage;
    }
    function getRandomInt(min, max) {
        // round min value upwards to next integer value
        min = Math.ceil(min);
        // round max value downwards to next integer value
        max = Math.floor(max);
        // return a random value where max is inclusive and minimum is exclusive
        return Math.floor(Math.random() * (max - min) + min);
    }
});