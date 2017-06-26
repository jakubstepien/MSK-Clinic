package mskClinic.waitingRoom;

import hla.rti1516e.*;
import hla.rti1516e.encoding.DecoderException;
import hla.rti1516e.encoding.HLAinteger32BE;
import hla.rti1516e.exceptions.FederateInternalError;
import org.portico.impl.hla1516e.types.time.DoubleTime;

import java.util.ArrayList;
import java.util.List;


public class WaitingRoomAmbassador extends NullFederateAmbassador {

    //----------------------------------------------------------
    //                    STATIC VARIABLES
    //----------------------------------------------------------

    //----------------------------------------------------------
    //                   INSTANCE VARIABLES
    //----------------------------------------------------------
    private  WaitingRoomFederate federate;
    protected double federateTime        = 0.0;
    protected double grantedTime         = 0.0;
    protected double federateLookahead   = 1.0;

    protected boolean isRegulating       = false;
    protected boolean isConstrained      = false;
    protected boolean isAdvancing        = false;

    protected boolean isAnnounced        = false;
    protected boolean isReadyToRun       = false;
    protected boolean running 			 = true;

    protected List<Integer> registeredPatients = new ArrayList<>();
    protected InteractionClassHandle patientRegisteredHandle;
    protected InteractionClassHandle doctorsAvailableHandle;
    protected InteractionClassHandle beginVisiteHandle;
    protected ParameterHandle patientIdHandle;
    protected ParameterHandle doctorsCountHandle;
    protected ParameterHandle patientIdInDoctorHandle;
    protected int doctorsCount;


    public WaitingRoomAmbassador(WaitingRoomFederate federate){
        this.federate = federate;
    }

    private double convertTime( LogicalTime logicalTime )
    {
        // PORTICO SPECIFIC!!
        return ((DoubleTime)logicalTime).getTime();
    }

    private void log( String message )
    {
        System.out.println( "WaitingRoomFederateAmbassador: " + message );
    }

    @Override
    public void synchronizationPointRegistrationFailed( String label,
                                                        SynchronizationPointFailureReason reason )
    {
        log( "Failed to register sync point: " + label + ", reason="+reason );
    }


    @Override
    public void synchronizationPointRegistrationSucceeded( String label )
    {
        log( "Successfully registered sync point: " + label );
    }

    @Override
    public void announceSynchronizationPoint( String label, byte[] tag )
    {
        log( "Synchronization point announced: " + label );
        if( label.equals(WaitingRoomFederate.READY_TO_RUN) )
            this.isAnnounced = true;
    }

    @Override
    public void federationSynchronized( String label, FederateHandleSet failed )
    {
        log( "Federation Synchronized: " + label );
        if( label.equals(WaitingRoomFederate.READY_TO_RUN) )
            this.isReadyToRun = true;
    }

    /**
     * The RTI has informed us that time regulation is now enabled.
     */
    public void timeRegulationEnabled( LogicalTime theFederateTime )
    {
        this.federateTime = convertTime( theFederateTime );
        this.isRegulating = true;
    }

    public void timeConstrainedEnabled( LogicalTime theFederateTime )
    {
        this.federateTime = convertTime( theFederateTime );
        this.isConstrained = true;
    }

    public void timeAdvanceGrant( LogicalTime theTime )
    {
        this.grantedTime = convertTime( theTime );
        this.isAdvancing = false;
    }


    @Override
    public void receiveInteraction( InteractionClassHandle interactionClass,
                                    ParameterHandleValueMap theParameters,
                                    byte[] tag,
                                    OrderType sentOrdering,
                                    TransportationTypeHandle theTransport,
                                    LogicalTime time,
                                    OrderType receivedOrdering,
                                    SupplementalReceiveInfo receiveInfo )
            throws FederateInternalError
    {
        if (interactionClass.equals(patientRegisteredHandle)) {
            try{
                int id = decodeInt(theParameters, patientIdHandle);
                registeredPatients.add(id);
                log("Time:" + time +" Received patient finished registering " + id );
            }
            catch (DecoderException e){
                e.printStackTrace();
            }
        }

        if (interactionClass.equals(doctorsAvailableHandle)) {
            try{
                doctorsCount = decodeInt(theParameters, doctorsCountHandle);
                log("Time: " + time +" Przychodnia wie że jest " +doctorsCount+" lekarzy");
//                int id = decodeInt(theParameters, patientIdHandle);
//                registeredPatients.add(id);
            }
            catch (DecoderException e){
                e.printStackTrace();
            }
        }
    }

    private int decodeInt(ParameterHandleValueMap theParameters, ParameterHandle handle) throws DecoderException{
        HLAinteger32BE openPar = federate.encoderFactory.createHLAinteger32BE();
        openPar.decode(theParameters.get(handle));
        return openPar.getValue();
    }

    @Override
    public void removeObjectInstance( ObjectInstanceHandle theObject,
                                      byte[] tag,
                                      OrderType sentOrdering,
                                      SupplementalRemoveInfo removeInfo )
            throws FederateInternalError
    {
        log( "Object Removed: handle=" + theObject );
    }

}
