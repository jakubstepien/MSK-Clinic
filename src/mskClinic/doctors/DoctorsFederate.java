package mskClinic.doctors;

import hla.rti1516e.*;
import hla.rti1516e.encoding.EncoderFactory;
import hla.rti1516e.exceptions.*;
import hla.rti1516e.time.HLAfloat64Time;
import hla.rti1516e.time.HLAfloat64TimeFactory;
import org.portico.impl.hla1516e.types.time.DoubleTime;
import org.portico.impl.hla1516e.types.time.DoubleTimeInterval;

import javax.swing.text.html.HTMLDocument;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Time;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
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
        int doctorsCount = rand.nextInt(20) + 1;

        boolean hasSendDoctors = false;
        while (true) {
            if (fedamb.clinicClosed) {
                return;
            }
            double timeToAdvance = fedamb.federateTime + fedamb.federateLookahead;

            advanceTime(timeToAdvance);

            if (fedamb.grantedTime == timeToAdvance) {
                if (fedamb.openTime != -1 && !hasSendDoctors && fedamb.grantedTime >= fedamb.openTime - fedamb.federateLookahead) {
                    DoctorsAvailable(timeToAdvance + fedamb.federateLookahead, doctorsCount);
                    hasSendDoctors = true;
                }

                medicationPatient(timeToAdvance + fedamb.federateLookahead);

                log("Time " + timeToAdvance);
                fedamb.federateTime = timeToAdvance;
            }

            tick();
        }
    }

    private void setNextStep(double time) {
        nextStep = time + nextStepMin + rand.nextInt(10) * nexStepMultiplier;
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
        // log("Currently in clinic is " +doctorsCount+ " doctors");
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

    private void medicationPatient(double currentTime) throws RTIexception {
        Iterator it = fedamb.patientsMedicationTimeMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Double> pair = ((Map.Entry<Integer, Double>) it.next());
            if (currentTime >= pair.getValue()) {
                it.remove();
                //fedamb.patientsMedicationTimeMap.remove(pair.getKey());

                log("koniec wizyty" + pair.getKey());
                EndOfVisit(currentTime, pair.getKey(), pair.getValue());
            }
        }
    }

    private void EndOfVisit(double currentTime, int patientIdEndVisit, double timeEndVisit) throws RTIexception {
        InteractionClassHandle endVisitHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.EndVisit");
        ParameterHandleValueMap parameters = rtiamb.getParameterHandleValueMapFactory().create(0);

        ParameterHandle patientIdEndVisitHandle = rtiamb.getParameterHandle(endVisitHandle, "PatientIdEndVisit");
        parameters.put(patientIdEndVisitHandle, encoderFactory.createHLAinteger32BE(patientIdEndVisit).toByteArray());

        ParameterHandle timeEndVisitHandle = rtiamb.getParameterHandle(endVisitHandle, "TimeEndVisit");
        parameters.put(timeEndVisitHandle, encoderFactory.createHLAfloat64BE(timeEndVisit).toByteArray());

        HLAfloat64Time time = timeFactory.makeTime(currentTime);

        rtiamb.sendInteraction(endVisitHandle, parameters, generateTag(), time);
        log("EndOfVisit" + patientIdEndVisit);
    }

    private void DoctorsAvailable(double currentTime, int doctorsCount) throws RTIexception {
        InteractionClassHandle doctorsAvailableHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.DoctorsAvailable");
        ParameterHandleValueMap parameters = rtiamb.getParameterHandleValueMapFactory().create(0);

        ParameterHandle doctorsCountHandle = rtiamb.getParameterHandle(doctorsAvailableHandle, "DoctorsCount");
        parameters.put(doctorsCountHandle, encoderFactory.createHLAinteger32BE(doctorsCount).toByteArray());

        HLAfloat64Time time = timeFactory.makeTime(currentTime);

        rtiamb.sendInteraction(doctorsAvailableHandle, parameters, generateTag(), time);
        log("Currently in clinic is " + doctorsCount + " doctors");

        //currentId++;
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
//       InteractionClassHandle patientEnteredHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.PatientEnteredClinic");
//        rtiamb.publishInteractionClass(patientEnteredHandle);

        InteractionClassHandle openClinicHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.ClinicOpened");
        rtiamb.subscribeInteractionClass(openClinicHandle);

        InteractionClassHandle doctorsCountHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.DoctorsAvailable");
        rtiamb.publishInteractionClass(doctorsCountHandle);

        InteractionClassHandle closeClinicHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.ClinicClosed");
        rtiamb.subscribeInteractionClass(closeClinicHandle);
        fedamb.closeClinic = closeClinicHandle;

        ParameterHandleValueMap parameters = rtiamb.getParameterHandleValueMapFactory().create(0);
        ParameterHandle openTime = rtiamb.getParameterHandle(openClinicHandle, "OpenTime");
//       ParameterHandle closeTime = rtiamb.getParameterHandle(openClinicHandle, "CloseTime");
        fedamb.clinicOpenHandle = openClinicHandle;
        fedamb.doctorsCountHandle = doctorsCountHandle;
        fedamb.openingTimeHandle = openTime;
//       fedamb.closingTimeHandle = closeTime;

        InteractionClassHandle beginVisiteHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.BeginVisite");
        ParameterHandle patientID = rtiamb.getParameterHandle(beginVisiteHandle, "PatientIdInDoctor");
        fedamb.beginVisiteHandle = beginVisiteHandle;
        fedamb.patientIdInDoctorHandle = patientID;
        rtiamb.subscribeInteractionClass(beginVisiteHandle);

        InteractionClassHandle endVisitHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.EndVisit");
        ParameterHandle patientIdEndVisit = rtiamb.getParameterHandle(endVisitHandle, "PatientIdEndVisit");
        fedamb.endVisitHandle = endVisitHandle;
        fedamb.patientIdEndVisitHandle = patientIdEndVisit;
        rtiamb.publishInteractionClass(endVisitHandle);
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