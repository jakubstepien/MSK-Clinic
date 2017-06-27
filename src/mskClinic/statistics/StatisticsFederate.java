package mskClinic.statistics;

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
import java.util.HashMap;
import java.util.Random;

/**
 * Created by Jakub on 26.06.2017.
 */
public class StatisticsFederate {
    public static final String FederationName = "ClinicFederation";
    public static final String federateName = "Statistics";
    public static final String READY_TO_RUN = "ReadyToRun";

    private static final Random rand = new Random();
    private RTIambassador rtiamb;
    private StatisticsAmbassador fedamb;
    private final double timeStep = 1;
    private int currentId = 1;
    private double nextStep;
    private double nextStepMin = 2;
    private double nexStepMultiplier = 5;
    private HLAfloat64TimeFactory timeFactory;
    protected EncoderFactory encoderFactory;

    private HashMap<Integer, Double> patientsEntryTimes = new HashMap<Integer, Double>();
    private HashMap<Integer, Double> patientsVistTimes = new HashMap<Integer, Double>();

    public void mainLoop() throws Exception {
        while (true) {
            if (fedamb.clinicClosed) {
                break;
            }
            double timeToAdvance = fedamb.federateTime + timeStep;

            advanceTime(timeToAdvance);

            addEnteredPatientsToStatistics();
            addPatientsStartedVistToStatistics();

            if (fedamb.grantedTime == timeToAdvance) {
                timeToAdvance += fedamb.federateLookahead;
                log("Time " + timeToAdvance);
                fedamb.federateTime = timeToAdvance;
            }
            tick();
        }
        for(int patient : patientsVistTimes.keySet()){
            double entry = patientsEntryTimes.get(patient);
            double startedVisit = patientsVistTimes.get(patient);
            double wait = startedVisit - entry;
            log("Patient " + patient + " entered clinic at " + entry  + " started visit at " +startedVisit + " waited " + wait);
        }
    }

    private void addEnteredPatientsToStatistics() {
        fedamb.enteredPatients.forEach(f -> {
            patientsEntryTimes.put(f.getId(), f.getTime());
            log("Added patient " + f.getId() + " with entry time " + f.getTime() + " to statistics");
        });
        fedamb.enteredPatients.clear();
    }

    private void addPatientsStartedVistToStatistics() {
        fedamb.patientsStartedVisit.forEach(f -> {
            patientsVistTimes.put(f.getId(), f.getTime());
            log("Added patient started visit " + f.getId() + " with visit start time " + f.getTime() + " to statistics");
            double waitTime = patientsVistTimes.get(f.getId()) - patientsEntryTimes.get(f.getId());
            log("Patient " + f.getId() + " waited " + waitTime);
        });
        fedamb.patientsStartedVisit.clear();
    }

    public void runFederate() throws Exception {
        double openTime = rand.nextInt(40) + 20;
        log("Creating RTIambassador");
        rtiamb = RtiFactoryFactory.getRtiFactory().getRtiAmbassador();
        encoderFactory = RtiFactoryFactory.getRtiFactory().getEncoderFactory();

        // connect
        log("Connecting...");
        fedamb = new StatisticsAmbassador(this);
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
        rtiamb.resignFederationExecution(ResignAction.DELETE_OBJECTS);
        try {
            rtiamb.destroyFederationExecution(FederationName);
            log("Destroyed Federation");
        } catch (FederationExecutionDoesNotExist dne) {
            log("No need to destroy federation, it doesn't exist");
        } catch (FederatesCurrentlyJoined fcj) {
            log("Didn't destroy federation, federates still joined");
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
        ParameterHandleValueMap parameters = rtiamb.getParameterHandleValueMapFactory().create(0);
        ParameterHandle patientId = rtiamb.getParameterHandle(patientEnteredHandle, "PatientId");
        ParameterHandle patientEntryTime = rtiamb.getParameterHandle(patientEnteredHandle, "EntryTime");
        fedamb.patientEnteredClinicHandle = patientEnteredHandle;
        fedamb.patientIdHandle = patientId;
        fedamb.entryTimeHandle = patientEntryTime;
        rtiamb.subscribeInteractionClass(patientEnteredHandle);

        InteractionClassHandle closeClinicHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.ClinicClosed");
        rtiamb.subscribeInteractionClass(closeClinicHandle);
        fedamb.closeClinic = closeClinicHandle;

        InteractionClassHandle beginVisite = rtiamb.getInteractionClassHandle("HLAinteractionRoot.BeginVisite");
        ParameterHandle patientIdInDoctor = rtiamb.getParameterHandle(beginVisite, "PatientIdInDoctor");
        ParameterHandle vistTime = rtiamb.getParameterHandle(beginVisite, "StartVisitTime");
        fedamb.beginVisitHandle = beginVisite;
        fedamb.visitTimeHandle = vistTime;
        fedamb.patientIdInDoctorHandle = patientIdInDoctor;
        rtiamb.subscribeInteractionClass(beginVisite);
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
            new StatisticsFederate().runFederate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}