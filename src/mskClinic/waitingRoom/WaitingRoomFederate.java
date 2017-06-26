package mskClinic.waitingRoom;


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
import java.lang.reflect.Array;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class WaitingRoomFederate {
    public static final String FederationName = "ClinicFederation";
    public static final String federateName = "WaitingRoom";
    public static final String READY_TO_RUN = "ReadyToRun";

    private static final Random rand = new Random();
    private RTIambassador rtiamb;
    private WaitingRoomAmbassador fedamb;
    private final double timeStep = 1.0;
    private ObjectInstanceHandle storageHlaHandle;
    private HLAfloat64TimeFactory timeFactory;
    private List<Integer> patientIds = new ArrayList<>();
    //List<Integer> patientToDoctors= new ArrayList<>();
    private int sentPatientCount=0;
    int WaintngRoomSize = rand.nextInt(20);

    protected EncoderFactory encoderFactory;

    public void mainLoop() throws Exception{

        while (fedamb.running) {
            double timeToAdvance = fedamb.federateTime + timeStep;
            advanceTime(timeToAdvance);
            //
            addToWaintngRoom();
            setPatientToDoctors(timeToAdvance + fedamb.federateLookahead);

            if (fedamb.grantedTime == timeToAdvance) {
                timeToAdvance += fedamb.federateLookahead;
                log("Time: " + timeToAdvance);
                fedamb.federateTime = timeToAdvance;
            }

            tick();
        }
    }

    public void setPatientToDoctors (double time) throws Exception
    {
        while(sentPatientCount < fedamb.doctorsCount)
        {
            if( patientIds.size()>0)
            {
                Integer tempPatient = patientIds.get(0);
                //WyslanieDoLekarza(tempPatient);
                BeginVist(time,tempPatient);
                patientIds.remove(0);
                sentPatientCount++;
            }else return;
        }
    }

    public void addToWaintngRoom(){
        ArrayList<Integer> addedPatients = new ArrayList<>();
        fedamb.registeredPatients.stream().forEach(f -> {
            log("Added to waiting room: " + f);
            if(patientIds.size() <=WaintngRoomSize)
            {
                addedPatients.add(f);
                patientIds.add(f);
            }
        });
        log("In waiting room are :" + patientIds.toString());
        fedamb.registeredPatients.removeAll(addedPatients);
        log("Outside of waiting room :" + String.join(", ",fedamb.registeredPatients.stream().map(m -> m.toString()).collect(Collectors.toList())));
    }

    private void BeginVist(double currentTime, int id) throws RTIexception {
        InteractionClassHandle patientIdInDoctor = rtiamb.getInteractionClassHandle("HLAinteractionRoot.BeginVisite");
        ParameterHandleValueMap parameters = rtiamb.getParameterHandleValueMapFactory().create(0);

        ParameterHandle patientIDHandle = rtiamb.getParameterHandle(patientIdInDoctor, "PatientIdInDoctor");
        parameters.put(patientIDHandle, encoderFactory.createHLAinteger32BE(id).toByteArray());

        HLAfloat64Time time = timeFactory.makeTime(currentTime);

        rtiamb.sendInteraction(patientIdInDoctor, parameters, generateTag(), time);
        log("Wyslany do lekarza pacjęt " + id );

        //currentId++;
    }

    private byte[] generateTag() {
        return ("(timestamp) " + System.currentTimeMillis()).getBytes();
    }


    public void runFederate() throws Exception {

        log("Creating RTIambassador");
        rtiamb = RtiFactoryFactory.getRtiFactory().getRtiAmbassador();
        encoderFactory = RtiFactoryFactory.getRtiFactory().getEncoderFactory();

        // connect
        log("Connecting...");
        fedamb = new WaitingRoomAmbassador(this);
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


    private void advanceTime(double timeToAdvance) throws RTIexception {
        fedamb.isAdvancing = true;
        LogicalTime newTime = convertTime(timeToAdvance);
        rtiamb.timeAdvanceRequest(newTime);

        while (fedamb.isAdvancing) {
            tick();
        }
    }

    private void publishAndSubscribe() throws RTIexception {
        InteractionClassHandle openClinicHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.ClinicOpened");
        rtiamb.subscribeInteractionClass(openClinicHandle);

        InteractionClassHandle patientRegistered = rtiamb.getInteractionClassHandle("HLAinteractionRoot.PatientRegistered");
        ParameterHandle patientId = rtiamb.getParameterHandle(patientRegistered, "PatientId");
        fedamb.patientRegisteredHandle = patientRegistered;
        fedamb.patientIdHandle = patientId;
        rtiamb.subscribeInteractionClass(patientRegistered);

        InteractionClassHandle doctorsAvailable = rtiamb.getInteractionClassHandle("HLAinteractionRoot.DoctorsAvailable");
        ParameterHandle doctorsCount = rtiamb.getParameterHandle(doctorsAvailable, "DoctorsCount");
        fedamb.doctorsAvailableHandle = doctorsAvailable;
        fedamb.doctorsCountHandle = doctorsCount;
        rtiamb.subscribeInteractionClass(doctorsAvailable);

        InteractionClassHandle beginVisite = rtiamb.getInteractionClassHandle("HLAinteractionRoot.BeginVisite");
        ParameterHandle patientIdInDoctor = rtiamb.getParameterHandle(beginVisite, "PatientIdInDoctor");
        fedamb.beginVisiteHandle = beginVisite;
        fedamb.patientIdInDoctorHandle = patientIdInDoctor;
        rtiamb.publishInteractionClass(beginVisite);

        InteractionClassHandle endVisitHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.EndVisit");
        ParameterHandle patientIdEndVisit = rtiamb.getParameterHandle(endVisitHandle, "PatientIdEndVisit");
        fedamb.endVisitHandle = endVisitHandle;
        fedamb.patientIdEndVisitHandle = patientIdEndVisit;
        rtiamb.subscribeInteractionClass(endVisitHandle);
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
