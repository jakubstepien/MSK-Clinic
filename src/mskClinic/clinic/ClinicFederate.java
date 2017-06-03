package mskClinic.clinic;

import hla.rti1516e.*;
import hla.rti1516e.encoding.EncoderFactory;
import hla.rti1516e.exceptions.*;
import hla.rti1516e.time.HLAfloat64Time;
import hla.rti1516e.time.HLAfloat64TimeFactory;
import org.portico.impl.hla1516e.types.time.DoubleTime;
import org.portico.impl.hla1516e.types.time.DoubleTimeInterval;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Created by Jakub on 03.06.2017.
 */
public class ClinicFederate {
    public static final String FederationName = "ClinicFederation";
    public static final String federateName = "Clinic";
    public static final String READY_TO_RUN = "ReadyToRun";

    private static final Random rand = new Random();
    private double closeTime = 200;
    private RTIambassador rtiamb;
    private ClinicAmbassador fedamb;
    private boolean opened = false;
    private boolean canClose = false;
    private final double timeStep = 10.0;
    private int stock = 10;
    private ObjectInstanceHandle storageHlaHandle;
    private HLAfloat64TimeFactory timeFactory;
    protected EncoderFactory encoderFactory;
    private PriorityQueue<PatientInRegistering> patientsInRegistering = new PriorityQueue<>(new PatientInRegisteringComparator());

    private void mainLoop(double openTime) throws Exception {
        while (!canClose) {
            double timeToAdvance = getTimeToAdvance(openTime);
            advanceTime(timeToAdvance);
            refreshRegistering();
            if (fedamb.grantedTime == timeToAdvance) {
                timeToAdvance += fedamb.federateLookahead;
                log("Time " + timeToAdvance);
                if (!opened && timeToAdvance + fedamb.federateLookahead >= openTime) {
                    sendClinicOpen(timeToAdvance);
                    opened = true;
                }
                sendRegistrationFinishedInfo(timeToAdvance);
                fedamb.federateTime = timeToAdvance;
            }
            tick();
        }
    }

    private void sendRegistrationFinishedInfo(double currentTime) throws Exception {
        List<PatientInRegistering> nowFinished = patientsInRegistering.stream()
                .filter(f -> f.getFinishTime() <= currentTime)
                .collect(Collectors.toList());
        for (PatientInRegistering patient : nowFinished){
            sendFinieshedRegistrationInteraction(patient.getId(),currentTime);
            patientsInRegistering.remove(patient);
        }
    }

    private void sendFinieshedRegistrationInteraction(int id, double currentTime) throws Exception {
        log("Sending clinic Open");
        InteractionClassHandle openClinicHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.PatientRegistered");
        ParameterHandleValueMap parameters = rtiamb.getParameterHandleValueMapFactory().create(0);
        ParameterHandle patientId = rtiamb.getParameterHandle(openClinicHandle, "PatientId");

        parameters.put(patientId, encoderFactory.createHLAinteger32BE(id).toByteArray());
        HLAfloat64Time time = timeFactory.makeTime(currentTime);
        rtiamb.sendInteraction(openClinicHandle, parameters, generateTag(), time);
        log("Send: " + id + " currTime: " + currentTime);
    }

    private void refreshRegistering() {
        fedamb.enteredPatients.stream().forEach(f -> {
            int registrationTime = getRegistrationTime();
            log("Added to registration " + f + " " + registrationTime);
            patientsInRegistering.add(new PatientInRegistering(f,registrationTime));
        });
        fedamb.enteredPatients.clear();
    }

    private int getRegistrationTime() {
        return (int) (fedamb.federateTime + timeStep + rand.nextInt(10));
    }

    private double getTimeToAdvance(double openTime) {
        double timeToAdvance = fedamb.federateTime + timeStep;
        if (!opened && timeToAdvance > openTime - fedamb.federateLookahead) {
            timeToAdvance = openTime - fedamb.federateLookahead;
        } else if(patientsInRegistering.size() > 0) {
            double nextRegistrationFinish = patientsInRegistering.peek().getFinishTime();
            if (timeToAdvance >= nextRegistrationFinish - fedamb.federateLookahead
                    && nextRegistrationFinish - fedamb.federateLookahead > fedamb.federateTime) {
                timeToAdvance = nextRegistrationFinish - fedamb.federateLookahead;
            }
        }
        return timeToAdvance;
    }

    public void runFederate() throws Exception {
        double openTime = rand.nextInt(40) + 20;
        log("Creating RTIambassador");
        rtiamb = RtiFactoryFactory.getRtiFactory().getRtiAmbassador();
        encoderFactory = RtiFactoryFactory.getRtiFactory().getEncoderFactory();
        log("Clinic start scheduled to: " + openTime);
        log("Connecting...");
        fedamb = new ClinicAmbassador(this);
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
        mainLoop(openTime);
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

    private void sendClinicOpen(double currentTime) throws RTIexception {
        log("Sending clinic Open");
        InteractionClassHandle openClinicHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.ClinicOpened");
        ParameterHandleValueMap parameters = rtiamb.getParameterHandleValueMapFactory().create(0);
        ParameterHandle openTime = rtiamb.getParameterHandle(openClinicHandle, "OpenTime");
        ParameterHandle closeTime = rtiamb.getParameterHandle(openClinicHandle, "CloseTime");

        parameters.put(openTime, encoderFactory.createHLAfloat64BE(currentTime).toByteArray());
        parameters.put(closeTime, encoderFactory.createHLAfloat64BE(this.closeTime).toByteArray());

        HLAfloat64Time time = timeFactory.makeTime(currentTime);
        rtiamb.sendInteraction(openClinicHandle, parameters, generateTag(), time);
    }

    private byte[] generateTag() {
        return ("(timestamp) " + System.currentTimeMillis()).getBytes();
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
        rtiamb.subscribeObjectClassAttributes(classHandle, attributes);

        InteractionClassHandle openClinicHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.ClinicOpened");
        rtiamb.publishInteractionClass(openClinicHandle);

        InteractionClassHandle patientRegistered = rtiamb.getInteractionClassHandle("HLAinteractionRoot.PatientRegistered");
        rtiamb.publishInteractionClass(patientRegistered);

        InteractionClassHandle patientEnteredHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.PatientEnteredClinic");
        ParameterHandleValueMap parameters = rtiamb.getParameterHandleValueMapFactory().create(0);
        ParameterHandle patientId = rtiamb.getParameterHandle(patientEnteredHandle, "PatientId");
        fedamb.patientEnteredClinicHandle = patientEnteredHandle;
        fedamb.patientIdHandle = patientId;
        rtiamb.subscribeInteractionClass(patientEnteredHandle);
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
        System.out.println(federateName + "   : " + message);
    }

    public static void main(String[] args) {
        try {
            new ClinicFederate().runFederate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}