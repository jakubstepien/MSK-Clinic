package mskClinic.statistics;

import hla.rti1516.*;
import hla.rti1516e.*;
import hla.rti1516e.FederateAmbassador;
import hla.rti1516e.FederateHandleSet;
import hla.rti1516e.InteractionClassHandle;
import hla.rti1516e.LogicalTime;
import hla.rti1516e.ObjectInstanceHandle;
import hla.rti1516e.OrderType;
import hla.rti1516e.ParameterHandle;
import hla.rti1516e.ParameterHandleValueMap;
import hla.rti1516e.SynchronizationPointFailureReason;
import hla.rti1516e.encoding.DecoderException;
import hla.rti1516e.encoding.HLAfloat64BE;
import hla.rti1516e.encoding.HLAinteger32BE;
import hla.rti1516e.exceptions.FederateInternalError;
import mskClinic.patients.PatientsFederate;
import org.portico.impl.hla1516e.types.time.DoubleTime;

import java.util.ArrayList;

/**
 * Created by Jakub on 26.06.2017.
 */
public class StatisticsAmbassador extends NullFederateAmbassador {

    //----------------------------------------------------------
    //                    STATIC VARIABLES
    //----------------------------------------------------------

    //----------------------------------------------------------
    //                   INSTANCE VARIABLES
    //----------------------------------------------------------
    private StatisticsFederate federate;

    protected double federateTime = 0.0;
    protected double grantedTime = 0.0;
    protected double federateLookahead = 1.0;

    protected boolean isRegulating = false;
    protected boolean isConstrained = false;
    protected boolean isAdvancing = false;

    protected boolean isAnnounced = false;
    protected boolean isReadyToRun = false;
    protected InteractionClassHandle patientEnteredClinicHandle;
    protected ParameterHandle entryTimeHandle;
    protected ParameterHandle patientIdHandle;

    protected InteractionClassHandle beginVisitHandle;
    protected ParameterHandle patientIdInDoctorHandle;
    protected ArrayList<PatientEnteredEvent> enteredPatients = new ArrayList<>();
    protected ArrayList<PatientEnteredEvent> patientsStartedVisit = new ArrayList<>();


    public StatisticsAmbassador(StatisticsFederate federate) {
        this.federate = federate;
    }

    private double convertTime(LogicalTime logicalTime) {
        // PORTICO SPECIFIC!!
        return ((DoubleTime) logicalTime).getTime();
    }

    private void log(String message) {
        System.out.println("StatisticsFederateAmbassador: " + message);
    }

    @Override
    public void synchronizationPointRegistrationFailed(String label,
                                                       SynchronizationPointFailureReason reason) {
        log("Failed to register sync point: " + label + ", reason=" + reason);
    }


    @Override
    public void synchronizationPointRegistrationSucceeded(String label) {
        log("Successfully registered sync point: " + label);
    }

    @Override
    public void announceSynchronizationPoint(String label, byte[] tag) {
        log("Synchronization point announced: " + label);
        if (label.equals(PatientsFederate.READY_TO_RUN))
            this.isAnnounced = true;
    }

    @Override
    public void federationSynchronized(String label, FederateHandleSet failed) {
        log("Federation Synchronized: " + label);
        if (label.equals(PatientsFederate.READY_TO_RUN))
            this.isReadyToRun = true;
    }

    public void timeRegulationEnabled(LogicalTime theFederateTime) {
        this.federateTime = convertTime(theFederateTime);
        this.isRegulating = true;
    }

    public void timeConstrainedEnabled(LogicalTime theFederateTime) {
        this.federateTime = convertTime(theFederateTime);
        this.isConstrained = true;
    }

    public void timeAdvanceGrant(LogicalTime theTime) {
        this.grantedTime = convertTime(theTime);
        this.isAdvancing = false;
    }


    @Override
    public void receiveInteraction(InteractionClassHandle interactionClass,
                                   ParameterHandleValueMap theParameters,
                                   byte[] tag,
                                   OrderType sentOrdering,
                                   TransportationTypeHandle theTransport,
                                   FederateAmbassador.SupplementalReceiveInfo receiveInfo)
            throws FederateInternalError {
        this.receiveInteraction(interactionClass,
                theParameters,
                tag,
                sentOrdering,
                theTransport,
                null,
                sentOrdering,
                receiveInfo);
    }

    @Override
    public void receiveInteraction(InteractionClassHandle interactionClass,
                                   ParameterHandleValueMap theParameters,
                                   byte[] tag,
                                   OrderType sentOrdering,
                                   TransportationTypeHandle theTransport,
                                   LogicalTime time,
                                   OrderType receivedOrdering,
                                   FederateAmbassador.SupplementalReceiveInfo receiveInfo)
            throws FederateInternalError {
        if (interactionClass.equals(patientEnteredClinicHandle)) {
            try{
                double entryTime = decodeDouble(theParameters, entryTimeHandle);
                int patientId = decodeInt(theParameters, patientIdHandle);
                enteredPatients.add(new PatientEnteredEvent(patientId,entryTime));
                log("Received patient entered clinic id:" + patientId + " time:" + entryTime);

            }
            catch (DecoderException e){
                e.printStackTrace();
            }
        } else if(interactionClass.equals(beginVisitHandle)){
            try{
                int patientId = decodeInt(theParameters, patientIdInDoctorHandle);
                //TODO //TODOTODOTODOTODOTODOTODOTODOTODOTODOTODOTODOTODOTODOTODOTODOTODOTODOTODOTODOTODOTODOTODOTODOTODOTODOTODOTODOTODOTODOTODOTODOTODOTODOTODOTODOTODOTODOTODOTODOTODOTODOTODOTODOTODOTODOTODOTODOTODOTODOTODOTODOTODOTODOTODOTODOTODOTODOTODOTODOTODOTODOTODOTODOTODOTODOTODOTODOTODOTODOTODOTODOTODO
                patientsStartedVisit.add(new PatientEnteredEvent(patientId, -4654));
                log("Received patient started visit clinic id:" + patientId + " time:" + time);
            }
            catch (DecoderException e){
                e.printStackTrace();
            }
        }
    }

    private double decodeDouble(ParameterHandleValueMap theParameters, ParameterHandle handle) throws DecoderException {
        HLAfloat64BE openPar = federate.encoderFactory.createHLAfloat64BE();
        openPar.decode(theParameters.get(handle));
        return openPar.getValue();
    }

    private int decodeInt(ParameterHandleValueMap theParameters, ParameterHandle handle) throws DecoderException {
        HLAinteger32BE openPar = federate.encoderFactory.createHLAinteger32BE();
        openPar.decode(theParameters.get(handle));
        return openPar.getValue();
    }

    @Override
    public void removeObjectInstance(ObjectInstanceHandle theObject,
                                     byte[] tag,
                                     OrderType sentOrdering,
                                     FederateAmbassador.SupplementalRemoveInfo removeInfo)
            throws FederateInternalError {
        log("Object Removed: handle=" + theObject);
    }

}
