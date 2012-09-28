package module;

import javax.xml.bind.JAXBException;

import org.jivesoftware.smack.XMPPException;

import service.PushService;
import service.XmppService;
import service.oadr.EiEventService;

import com.google.inject.Provides;
import com.google.inject.Singleton;

public class Dependencies {
  
    @Provides
    @Singleton
    public XmppService makeXmppService() throws XMPPException, InstantiationException, IllegalAccessException, JAXBException{
        return new XmppService();
    }
    
    @Provides
    @Singleton
    public EiEventService makeEiEventService(){
        return new EiEventService();
    }
    
    @Provides
    @Singleton
    public PushService makePushService(){
        return new PushService();
    }
  
}