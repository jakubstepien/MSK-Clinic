package mskClinic.doctors;

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
import java.util.Random;

/**
 * Created by Malgorzata on 2017-06-14.
 */
public class DoctorsFederate {
    public static final String FederationName = "ClinicFederation";
    public static final String federateName = "Doctors";
    public static final String READY_TO_RUN = "ReadyToRun";

    private static final Random rand = new Random();
    private RTIambassador rtiamb;
    private DoctorsAmbassador fedamb;
    private boolean clinicOpened = false;
    private boolean closing = false;
    private final double timeStep = 1;
    private int currentId = 1;
    private double nextStep;
    private double nextStepMin = 2;
    private double nexStepMultiplier = 5;
    private ObjectInstanceHandle storageHlaHandle;
    private HLAfloat64TimeFactory timeFactory;
    protected EncoderFactory encoderFactory;

    public void mainLoop() throws Exception {
        while (true) {
            boolean sendPatient = false;
            double timeToAdvance;

            if(!clinicOpened){
                if(fedamb.openTime != -1){
                    timeToAdvance = fedamb.openTime;
                    clinicOpened = true;
                    if(timeToAdvance < fedamb.federateTime){
                        timeToAdvance = fedamb.federateTime  + timeStep;
                        setNextStep(timeToAdvance);
                    }
                    else{
                        setNextStep(fedamb.openTime + fedamb.federateLookahead);
                    }
                }else{
                    timeToAdvance = fedamb.federateTime + timeStep;
                }
            }
            else {
                if(!closing && nextStep < fedamb.closeTime){
                    timeToAdvance = nextStep - fedamb.federateLookahead;
                    sendPatient = true;
                }
                else{
                    timeToAdvance = fedamb.closeTime - fedamb.federateLookahead;
                    closing = true;
                }
            }
            advanceTime(timeToAdvance);

            if(closing){
                log("Stopping spawning doctors");
                rtiamb.resignFederationExecution( ResignAction.DELETE_OBJECTS );
                break;
            }

            if (fedamb.grantedTime == timeToAdvance) {
                timeToAdvance += fedamb.federateLookahead;
                log("Time " + timeToAdvance);
                if (sendPatient) {
                    sendPatientEnteredClinic(timeToAdvance);
                    setNextStep(timeToAdvance);
                }
                fedamb.federateTime = timeToAdvance;
            }
            tick();
        }
    }

    private void setNextStep(double time) {
        nextStep = time +  nextStepMin + rand.nextInt(10) * nexStepMultiplier;
        log("next step " + nextStep);
    }

    public void runFederate() throws Exception {
        double openTime = rand.nextInt(40) + 20;
        log("Creating RTIambassador");
        rtiamb = RtiFactoryFactory.getRtiFactory().getRtiAmbassador();
        encoderFactory = RtiFactoryFactory.getRtiFactory().getEncoderFactory();

        // connect
        log("Connecting...");
        fedamb = new DoctorsAmbassador(this);
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
        mainLoop();
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

    private void sendPatientEnteredClinic(double currentTime) throws RTIexception {
        InteractionClassHandle patientEnteredHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.PatientEnteredClinic");
        ParameterHandleValueMap parameters = rtiamb.getParameterHandleValueMapFactory().create(0);
        ParameterHandle patientId = rtiamb.getParameterHandle(patientEnteredHandle, "PatientId");
        ParameterHandle entryTime = rtiamb.getParameterHandle(patientEnteredHandle,"EntryTime");

        parameters.put(patientId, encoderFactory.createHLAinteger32BE(currentId).toByteArray());
        parameters.put(entryTime, encoderFactory.createHLAfloat64BE(currentTime).toByteArray());

        HLAfloat64Time time = timeFactory.makeTime(currentTime);
        rtiamb.sendInteraction(patientEnteredHandle, parameters, generateTag(), time);
        log("added patient : " + currentId);
        currentId++;
    }

    private byte[] generateTag() {
        return ("(timestamp) " + System.currentTimeMillis()).getBytes();
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
        InteractionClassHandle patientEnteredHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.PatientEnteredClinic");
        rtiamb.publishInteractionClass(patientEnteredHandle);

        InteractionClassHandle openClinicHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.ClinicOpened");
        rtiamb.subscribeInteractionClass(openClinicHandle);
        ParameterHandleValueMap parameters = rtiamb.getParameterHandleValueMapFactory().create(0);
        ParameterHandle openTime = rtiamb.getParameterHandle(openClinicHandle, "OpenTime");
        ParameterHandle closeTime = rtiamb.getParameterHandle(openClinicHandle, "CloseTime");
        fedamb.clinicOpenHandle = openClinicHandle;
        fedamb.openingTimeHandle = openTime;
        fedamb.closingTimeHandle = closeTime;
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
            new DoctorsFederate().runFederate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}