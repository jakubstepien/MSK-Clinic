package mskClinic.doctors;

import hla.rti1516e.*;
import hla.rti1516e.encoding.DecoderException;
import hla.rti1516e.encoding.HLAfloat64BE;
import hla.rti1516e.exceptions.FederateInternalError;
import org.portico.impl.hla1516e.types.time.DoubleTime;

/**
 * Created by Malgorzata on 2017-06-14.
 */
public class DoctorsAmbassador extends NullFederateAmbassador {

    //----------------------------------------------------------
    //                    STATIC VARIABLES
    //----------------------------------------------------------

    //----------------------------------------------------------
    //                   INSTANCE VARIABLES
    //----------------------------------------------------------
    private DoctorsFederate federate;

    protected double federateTime = 0.0;
    protected double grantedTime = 0.0;
    protected double federateLookahead = 1.0;
    protected double closeTime = -1;
    protected double openTime = -1;

    protected boolean isRegulating = false;
    protected boolean isConstrained = false;
    protected boolean isAdvancing = false;

    protected boolean isAnnounced = false;
    protected boolean isReadyToRun = false;
    protected InteractionClassHandle clinicOpenHandle;
    protected ParameterHandle openingTimeHandle;
    protected ParameterHandle closingTimeHandle;

    public DoctorsAmbassador(DoctorsFederate federate) {
        this.federate = federate;
    }

    private double convertTime(LogicalTime logicalTime) {
        // PORTICO SPECIFIC!!
        return ((DoubleTime) logicalTime).getTime();
    }

    private void log(String message) {
        System.out.println("DoctorsFederateAmbassador2: " + message);
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
        if (label.equals(DoctorsFederate.READY_TO_RUN))
            this.isAnnounced = true;
    }

    @Override
    public void federationSynchronized(String label, FederateHandleSet failed) {
        log("Federation Synchronized: " + label);
        if (label.equals(DoctorsFederate.READY_TO_RUN))
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
                                   SupplementalReceiveInfo receiveInfo)
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
                                   SupplementalReceiveInfo receiveInfo)
            throws FederateInternalError {
        if (interactionClass.equals(clinicOpenHandle)) {
            try{
                this.openTime = decodeDouble(theParameters, openingTimeHandle);
                this.closeTime = decodeDouble(theParameters, closingTimeHandle);
                log("Received clinic open interaction " + this.openTime + " " + this.closeTime);
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

    @Override
    public void removeObjectInstance(ObjectInstanceHandle theObject,
                                     byte[] tag,
                                     OrderType sentOrdering,
                                     SupplementalRemoveInfo removeInfo)
            throws FederateInternalError {
        log("Object Removed: handle=" + theObject);
    }

}

