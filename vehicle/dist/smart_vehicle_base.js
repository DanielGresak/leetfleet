"use strict";
var __awaiter = (this && this.__awaiter) || function (thisArg, _arguments, P, generator) {
    function adopt(value) { return value instanceof P ? value : new P(function (resolve) { resolve(value); }); }
    return new (P || (P = Promise))(function (resolve, reject) {
        function fulfilled(value) { try { step(generator.next(value)); } catch (e) { reject(e); } }
        function rejected(value) { try { step(generator["throw"](value)); } catch (e) { reject(e); } }
        function step(result) { result.done ? resolve(result.value) : adopt(result.value).then(fulfilled, rejected); }
        step((generator = generator.apply(thisArg, _arguments || [])).next());
    });
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.WotDevice = void 0;
const request = require("request");
//  import Ajv = require("ajv");
//  var ajv = new Ajv();
const Ajv = require("ajv");
const ajv = new Ajv(); // options can be passed, e.g. {allErrors: true}
class WotDevice {
    constructor(deviceWoT, tdDirectory) {
        // Thing Model -> fill in the empty quotation marks
        this.thingModel = {
            "@context": ["https://www.w3.org/2019/wot/td/v1", { "@language": "en" }],
            "@type": "",
            id: "new:thing",
            title: "",
            description: "",
            securityDefinitions: {
                "": {
                    scheme: "nosec",
                },
            },
            security: "",
            properties: {
                myProperty: {
                    title: "A short title for User Interfaces",
                    description: "A longer string for humans to read and understand",
                    unit: "",
                    type: "null",
                },
            },
            actions: {
                myAction: {
                    title: "A short title for User Interfaces",
                    description: "A longer string for humans to read and understand",
                    input: {
                        unit: "",
                        type: "number",
                    },
                    out: {
                        unit: "",
                        type: "string",
                    },
                },
            },
            events: {
                myEvent: {
                    title: "A short title for User Interfaces",
                    description: "A longer string for humans to read and understand",
                    data: {
                        unit: "",
                        type: "null",
                    },
                },
            },
        };
        // initialze WotDevice parameters
        this.deviceWoT = deviceWoT;
        if (tdDirectory)
            this.tdDirectory = tdDirectory;
    }
    startDevice() {
        return __awaiter(this, void 0, void 0, function* () {
            console.log(`Producing Thing: ${this.thingModel.title}`);
            const exposedThing = yield this.deviceWoT.produce(this.thingModel);
            console.log("Thing produced");
            this.thing = exposedThing;
            this.td = exposedThing.getThingDescription();
            this.initializeProperties(); // Initialize properties and add their handlers
            this.initializeActions(); // Initialize actions and add their handlers
            // Events do not need to be initialzed, can be emited from anywhere
            console.log(`Exposing Thing: ${this.thingModel.title}`);
            yield this.thing.expose(); // Expose thing
            console.log("Exposed Thing");
            if (this.tdDirectory) {
                this.register(this.tdDirectory);
            }
            this.listenToMyEvent(); // used to listen to specific events provided by a library. If you don't have events, simply remove it
        });
    }
    register(directory) {
        console.log("Registering TD in directory: " + directory);
        request.post(directory, { json: this.thing.getThingDescription() }, (error, response, body) => {
            if (!error && response.statusCode < 300) {
                console.log("TD registered!");
            }
            else {
                console.debug(error);
                console.debug(response);
                console.warn("Failed to register TD. Will try again in 10 Seconds...");
                setTimeout(() => {
                    this.register(directory);
                }, 10000);
                return;
            }
        });
    }
    myPropertyReadHandler(options) {
        return __awaiter(this, void 0, void 0, function* () {
            // read something
            return this.myProperty;
        });
    }
    myPropertyWriteHandler(inputData, options) {
        return __awaiter(this, void 0, void 0, function* () {
            // write something to property
            this.myProperty = yield inputData.value();
        });
    }
    myActionHandler(inputData, options) {
        return __awaiter(this, void 0, void 0, function* () {
            // do something with inputData if available
            let dataValue;
            if (inputData) {
                dataValue = yield inputData.value();
            }
            if (dataValue) {
                this.thing.emitEvent("myEvent", null); // Emiting an event (may be removed; only for demonstration purposes)
            }
            let outputData = "";
            // resolve that with outputData if available, else resolve that action was successful without returning anything
            if (outputData) {
                return outputData;
            }
            else {
                return null;
            }
        });
    }
    listenToMyEvent() {
        /*
        specialLibrary.getMyEvent()//change specialLibrary to your library
        .then((thisEvent) => {
            this.thing.emitEvent("myEvent",""); //change quotes to your own event data
        });
        */
    }
    initializeProperties() {
        //fill in add properties
        this.myProperty = ""; // replace quotes with the initial value
        this.thing.setPropertyReadHandler("myProperty", this.myPropertyReadHandler); // not applicable for write-only
        this.thing.setPropertyWriteHandler("myProperty", this.myPropertyWriteHandler); // not applicable for read-only
    }
    initializeActions() {
        //fill in add actions
        this.thing.setActionHandler("myAction", (inputData) => __awaiter(this, void 0, void 0, function* () {
            let dataValue = yield inputData.value();
            if (!ajv.validate(this.td.actions.myAction.input, dataValue)) {
                throw new Error("Invalid input");
            }
            else {
                return this.myActionHandler(inputData);
            }
        }));
    }
}
exports.WotDevice = WotDevice;
//# sourceMappingURL=smart_vehicle_base.js.map