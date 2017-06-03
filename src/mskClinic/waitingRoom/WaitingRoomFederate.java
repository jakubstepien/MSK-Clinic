package mskClinic.waitingRoom;


import hla.rti1516e.*;
import hla.rti1516e.encoding.EncoderFactory;
import hla.rti1516e.exceptions.*;
import hla.rti1516e.time.HLAfloat64TimeFactory;
import org.portico.impl.hla1516e.types.time.DoubleTime;
import org.portico.impl.hla1516e.types.time.DoubleTimeInterval;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;

public class WaitingRoomFederate {
    public static final String FederationName = "ClinicFederation";
    public static final String federateName = "WaitingRoom";
    public static final String READY_TO_RUN = "ReadyToRun";

    private RTIambassador rtiamb;
    private WaitingRoomAmbassador fedamb;
    private final double timeStep = 10.0;
    private int stock = 10;
    private ObjectInstanceHandle storageHlaHandle;
    private HLAfloat64TimeFactory timeFactory;
    protected EncoderFactory encoderFactory;

    public void runFederate() throws Exception {

        log("Creating RTIambassador");
        rtiamb = RtiFactoryFactory.getRtiFactory().getRtiAmbassador();
        encoderFactory = RtiFactoryFactory.getRtiFactory().getEncoderFactory();

        // connect
        log("Connecting...");
        fedamb = new WaitingRoomAmbassador();
        rtiamb.connect(fedamb, CallbackModel.HLA_EVOKED);

        log("Creating Federation...");
        try {
            URL[] modules = new URL[]{
                    (new File("Clinic.xml")).toURI().toURL(),
            };

            rtiamb.createFederationExecution(FederationName, modules);
            log("Created Federation");
        } catch (FederationExecutionAlreadyExists exists) {
            log("Didn't create federation, it already existed");
        } catch (MalformedURLException urle) {
            log("Exception loading one of the FOM modules from disk: " + urle.getMessage());
            urle.printStackTrace();
            return;
        }

        rtiamb.joinFederationExecution(federateName,            // name for the federate
                federateName + "Type",   // federate type
                FederationName);     // name of federation

        log("Joined Federation as " + federateName);

        this.timeFactory = (HLAfloat64TimeFactory) rtiamb.getTimeFactory();

        rtiamb.registerFederationSynchronizationPoint(READY_TO_RUN, null);

        while (fedamb.isAnnounced == false) {
            tick();
        }

        waitForUser();

        rtiamb.synchronizationPointAchieved(READY_TO_RUN);
        log("Achieved sync point: " + READY_TO_RUN + ", waiting for federation...");
        while (fedamb.isReadyToRun == false) {
            tick();
        }
        enableTimePolicy();
        publishAndSubscribe();
        registerWatingRoomObject();
        while (fedamb.running) {

            double timeToAdvance = fedamb.federateTime + timeStep;
            advanceTime(timeToAdvance);

            if (fedamb.grantedTime == timeToAdvance) {
                timeToAdvance += fedamb.federateLookahead;
                log("Time: " + timeToAdvance);
                updateHLAObject(timeToAdvance);
                fedamb.federateTime = timeToAdvance;
            }

            tick();
        }

    }

    private void waitForUser() {
        log(" >>>>>>>>>> Press Enter to Continue <<<<<<<<<<");
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        try {
            reader.readLine();
        } catch (Exception e) {
            log("Error while waiting for user input: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void registerWatingRoomObject() throws RTIexception {
        ObjectClassHandle classHandle = rtiamb.getObjectClassHandle("ObjectRoot.WaitingRoom");
        this.storageHlaHandle = rtiamb.registerObjectInstance(classHandle);
    }

    private void updateHLAObject(double time) throws RTIexception {

    }

    private void advanceTime(double timeToAdvance) throws RTIexception {
        fedamb.isAdvancing = true;
        LogicalTime newTime = convertTime(timeToAdvance);
        rtiamb.timeAdvanceRequest(newTime);

        while (fedamb.isAdvancing) {
            tick();
        }
    }

    private void publishAndSubscribe() throws RTIexception {

        ObjectClassHandle classHandle = rtiamb.getObjectClassHandle("HLAobjectRoot.WaitingRoom");
        AttributeHandle sizeHandle = rtiamb.getAttributeHandle(classHandle, "NumberOfSeats");
        AttributeHandle peopleHandle = rtiamb.getAttributeHandle(classHandle, "NumberOfPeopleInside");

        AttributeHandleSet attributes = rtiamb.getAttributeHandleSetFactory().create();
        attributes.add(sizeHandle);
        attributes.add(peopleHandle);

        rtiamb.publishObjectClassAttributes(classHandle, attributes);
        rtiamb.subscribeObjectClassAttributes(classHandle, attributes);

        InteractionClassHandle openClinicHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.ClinicOpened");
        rtiamb.subscribeInteractionClass(openClinicHandle);

    }

    private void enableTimePolicy() throws RTIexception {
        LogicalTime currentTime = convertTime(fedamb.federateTime);
        LogicalTimeInterval lookahead = convertInterval(fedamb.federateLookahead);

        this.rtiamb.enableTimeRegulation(lookahead);

        while (fedamb.isRegulating == false) {
            tick();
        }

        this.rtiamb.enableTimeConstrained();

        while (fedamb.isConstrained == false) {
            tick();
        }
    }

    private LogicalTime convertTime(double time) {
        // PORTICO SPECIFIC!!
        return new DoubleTime(time);
    }

    private void tick() throws RTIinternalError, FederateNotExecutionMember, CallNotAllowedFromWithinCallback {
        rtiamb.evokeMultipleCallbacks(0.1, 0.2);
    }

    /**
     * Same as for {@link #convertTime(double)}
     */
    private LogicalTimeInterval convertInterval(double time) {
        // PORTICO SPECIFIC!!
        return new DoubleTimeInterval(time);
    }

    private void log(String message) {
        System.out.println(federateName + "Federate   : " + message);
    }

    public static void main(String[] args) {
        try {
            new WaitingRoomFederate().runFederate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}