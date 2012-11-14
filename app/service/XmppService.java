package service;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.datatype.DatatypeFactory;

import jaxb.JAXBManager;

import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Packet;

import play.Logger;
import play.db.jpa.Transactional;
import service.oadr.EiEventService;
import xmpp.OADR2IQ;
import xmpp.OADR2PacketExtension;

import com.google.inject.Inject;

/**
 * XMPPService is used to establish and hold the XMPPConnection
 * to be used for sending and creating events
 * 
 * @author Jeff LaJoie
 *
 */
public class XmppService {

    private static volatile XmppService instance = null;
        
    static final String OADR2_XMLNS = "http://openadr.org/oadr-2.0a/2012/07";
    
    private ConnectionConfiguration connConfig = new ConnectionConfiguration("msawant-mbp.local", 5222);
    
    private static XMPPConnection vtnConnection;
    
    @Inject static PushService pushService;// = new PushService();
    @Inject static EiEventService eventService;// = new EiEventService();
    
    //TODO add these to a config file like spring config or something, hardcoded for now
    private String vtnUsername = "xmpp-vtn";
    private String vtnPassword = "xmpp-pass";
    
    private Marshaller marshaller;
    DatatypeFactory xmlDataTypeFac;
    
    static EntityManagerFactory entityManagerFactory = Persistence.createEntityManagerFactory("Events");
    static EntityManager entityManager = entityManagerFactory.createEntityManager();
    
    /**
     * Constructor to establish the XMPP connection
     * 
     * @throws XMPPException
     * @throws InstantiationException
     * @throws IllegalAccessException
     * @throws JAXBException
     */
    public XmppService() throws XMPPException, InstantiationException, IllegalAccessException, JAXBException{
        //Add for debugging
        //Connection.DEBUG_ENABLED = true;
        if(vtnConnection == null){
            vtnConnection = connect(vtnUsername, vtnPassword, "vtn");
        }
        
        JAXBManager jaxb = new JAXBManager();
        marshaller = jaxb.createMarshaller();
    }
    
    /**
     * Singleton getter for when Guice injection is not possible
     * 
     * @return the singleton XmppService
     */
    public static XmppService getInstance(){
        if(instance == null){
            synchronized(XmppService.class){
                if(instance == null){
                    try {
                        instance = new XmppService();
                    } catch (XMPPException e) {
                        Logger.error("XMPPException creating XMPPService.", e);
                    } catch (InstantiationException e) {
                        Logger.error("InstantiationException creating XMPPService.", e);
                    } catch (IllegalAccessException e) {
                        Logger.error("IllegalAccessException creating XMPPService.", e);
                    } catch (JAXBException e) {
                        Logger.error("JAXBException creating XMPPService.", e);
                    }
                }
            }
        }
        return instance;
    }
    
   
    /**
     * Adds a packet listener to the connection that handles all incoming packets
     * 
     * @return a PacketListener to be added to a connection
     */
    @Transactional
    public PacketListener oadrPacketListener(){
        return new PacketListener(){
            @Override
            @Transactional
            public void processPacket(Packet packet){
                OADR2PacketExtension extension = (OADR2PacketExtension)packet.getExtension(OADR2_XMLNS);
                Object payload = eventService.handleOadrPayload(extension.getPayload());
                if(payload != null){
                    sendObjectToJID(payload, packet.getFrom());
                }
            }         
        };
    }
    
    /**
     * A packet filter to only accept packets with the OADR2_XMLNS
     * 
     * @return a PacketFilter to be added to a PacketListener
     */
    public PacketFilter oadrPacketFilter(){
        return new PacketFilter(){
            @Override
            public boolean accept(Packet packet){                
                return packet.getExtension(OADR2_XMLNS) != null;
            }
        };
    }
    
    /**
     * Establish a connection for the XMPP server
     * 
     * @param username - Username to connect with
     * @param password - Password to connect with according to the username specified
     * @param resource - Resource to connect the XMPP to
     * @return
     * @throws InstantiationException
     * @throws IllegalAccessException
     * @throws XMPPException
     */
    public XMPPConnection connect(String username, String password, String resource) throws InstantiationException, IllegalAccessException, XMPPException{
       XMPPConnection connection = new XMPPConnection(connConfig);
       if(!connection.isConnected()){
           connection.connect();
           if(connection.getUser() == null && !connection.isAuthenticated()){
               connection.login(username, password, resource);
               connection.addPacketListener(oadrPacketListener(), oadrPacketFilter());
           }
       }
       return connection;
    }        
    
    /**
     * Sends an object to a JID
     * 
     * @param o - the Object to be sent
     * @param jid - the Jid to receive the object
     */
    public void sendObjectToJID(Object o, String jid){
        IQ iq = new OADR2IQ(new OADR2PacketExtension(o, marshaller));
        iq.setTo(jid);
        iq.setType(IQ.Type.SET);
        vtnConnection.sendPacket(iq);
    }
    
    /**
     * Send an object to a jid with the specified packetId
     * 
     * @param o - the Object to be sent
     * @param jid - the Jid to receive the object
     * @param packetId - the packetId the packet must contain
     */
    public void sendObjectToJID(Object o, String jid, String packetId){
        IQ iq = new OADR2IQ(new OADR2PacketExtension(o, marshaller));
        iq.setTo(jid);
        iq.setPacketID(packetId);
        iq.setType(IQ.Type.RESULT);
        vtnConnection.sendPacket(iq);
    }
    
}
