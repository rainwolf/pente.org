package org.pente.gameServer.server.ssl;

import java.lang.management.ManagementFactory;
import java.util.Set;
import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.ObjectName;

/** Reloads Tomcat's HTTPS connector certificates in-process over the platform MBean server. */
public final class TomcatSslReloader {

    private TomcatSslReloader() {
    }

    /** Invokes reloadSslHostConfigs on every https endpoint; returns the count, throws if none. */
    public static int reloadAll() throws JMException {
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        Set<ObjectName> names = mbs.queryNames(new ObjectName("Catalina:type=ThreadPool,name=*"), null);
        int count = 0;
        for (ObjectName n : names) {
            String name = n.getKeyProperty("name");
            if (name != null && name.contains("https")) {
                mbs.invoke(n, "reloadSslHostConfigs", new Object[0], new String[0]);
                count++;
            }
        }
        if (count == 0) {
            throw new JMException("No https connector endpoint MBean found");
        }
        return count;
    }
}
