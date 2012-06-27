/*
 * com.continuuity - Copyright (c) 2012 Continuuity Inc. All rights reserved.
 */
package com.continuuity;

import ch.qos.logback.classic.Logger;
import com.continuuity.common.conf.CConfiguration;
import com.continuuity.common.conf.Constants;
import com.continuuity.common.utils.Copyright;
import com.continuuity.common.zookeeper.InMemoryZookeeper;
import com.continuuity.data.runtime.DataFabricModules;
import com.continuuity.flow.manager.server.FARServer;
import com.continuuity.flow.manager.server.FlowManagerServer;
import com.continuuity.flow.runtime.FARModules;
import com.continuuity.flow.runtime.FlowManagerModules;
import com.continuuity.gateway.Gateway;
import com.continuuity.gateway.runtime.GatewayModules;
import com.continuuity.metrics.service.MetricsServer;
import com.continuuity.runtime.MetricsModules;
import com.google.common.base.Preconditions;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import org.apache.commons.lang.StringUtils;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetAddress;

/**
 * SingleNodeMain is the master main method for the Continuuity single node
 * platform. This is where we load all our external configuration and bootstrap
 * all the services that comprise the platform.
 */
public class SingleNodeMain {

  /**
   * This is our Logger instance
   */
  private static final Logger logger =
      (Logger)LoggerFactory.getLogger(SingleNodeMain.class);

  /**
   * This is the Zookeeper service.
   *
   * TODO: Find somewhere to create this so we can inject it
   */
  private InMemoryZookeeper zookeeper;

  /**
   * This is the Gateway service
   */
  @Inject
  private Gateway theGateway;

  /**
   * This is the Metrics Monitor service
   */
  @Inject
  private MetricsServer theOverlord;

  /**
   * This is the FAR server.
   */
  @Inject
  private FARServer theFARServer;

  /**
   * This is the FlowManager server
   */
  @Inject
  private FlowManagerServer theFlowManager;

  /**
   * This is the WebApp Service
   */
  private WebCloudAppService theWebApp;

  /**
   * This is our universal configurations object.
   */
  private CConfiguration myConfiguration;



  /**
   * Bootstrap is where we initialize all the services that make up the
   * SingleNode version.
   *
   * TODO: Create a "service" interface that all our top level services can
   * implement. We can then clean up the code below and generify it.
   */
  private void bootStrapServices() throws Exception {
    Preconditions.checkNotNull(theOverlord);
    Preconditions.checkNotNull(theGateway);
    Preconditions.checkNotNull(theFARServer);
    Preconditions.checkNotNull(theFlowManager);

    System.out.println(" Starting Zookeeper Service");
    startZookeeper();
    System.out.println(" Starting Metrics Service");
    theOverlord.start(null, myConfiguration);
    System.out.println(" Starting Gateway Service");
    theGateway.start(null, myConfiguration);
    System.out.println(" Starting FlowArchive Service");
    theFARServer.start(null, myConfiguration);
    System.out.println(" Starting FlowManager Service");
    theFlowManager.start(null, myConfiguration);
    System.out.println(" Starting Monitoring Webapp");
    theWebApp = new WebCloudAppService();
    theWebApp.start(null, myConfiguration);
    String hostname = InetAddress.getLocalHost().getHostName();
    System.out.println(" Bigflow started successfully. Connect to UI : http://" + hostname + ":9999");
  } // end of bootStrapServices

  /**
   * Start Zookeeper attempts to start our single node ZK instance. It requires
   * two configuration values to be set somewhere in our config files:
   *
   * <ul>
   *   <li>zookeeper.port</li>
   *   <li>zookeeper.datadir</li>
   * </ul>
   *
   * We also push the ZK ensemble setting back into myConfiguration for
   * use by the other services.
   *
   * @throws InterruptedException
   * @throws IOException
   */
  private void startZookeeper() throws InterruptedException, IOException {
    zookeeper =
      new InMemoryZookeeper(
        Integer.parseInt(myConfiguration.get("zookeeper.port")),
        new File(myConfiguration.get("zookeeper.datadir")) );

    // Set the connection string about where ZK server started on */
    myConfiguration.set(Constants.CFG_ZOOKEEPER_ENSEMBLE,
      zookeeper.getConnectionString());
  }


  /**
   * Load Configuration looks for all of the config xml files in the resources
   * directory, and loads all of the properties into those files.
   */
  private void loadConfiguration() {

    // Create our config object
    myConfiguration = CConfiguration.create();

    // Clear all of the hadoop settings
    myConfiguration.clear();

    // TODO: Make this generic and scan for files before adding them
    myConfiguration.addResource("continuuity-flow.xml");
    myConfiguration.addResource("continuuity-gateway.xml");
    myConfiguration.addResource("continuuity-webapp.xml");
    myConfiguration.addResource("continuuity-overlord.xml");
  } // end of loadConfiguration


  /**
   * Print the usage statement and return null.
   *
   * @param error indicates whether this was invoked as the result of an error
   * @throws IllegalArgumentException in case of error
   */
  static void usage(boolean error) {
    PrintStream out = (error ? System.err : System.out);
    Copyright.print(out);
    out.println("Requirements: ");
    out.println("  Java:    JDK 1.6+ must be installed and JAVA_HOME environment variable set to the java executable");
    out.println("  Node.js: Node.js must be installed (obtain from http://nodejs.org/#download).  ");
    out.println("           The \"node\" executable must be in the system $PATH environment variable");
    out.println("");
    out.println("Usage: ");
    out.println("  ./bigFlow [options]");
    out.println("");
    out.println("Additional options:");
    out.println("  --help      To print this message");
    out.println("");
    if (error) {
      throw new IllegalArgumentException();
    }
  }


  /**
   * The root of all goodness!
   *
   * @param args Our cmdline arguments
   */
  public static void main(String[] args) {
    // We only support 'help' command line options currently
    if (args.length > 0) {
      if ("--help".equals(args[0]) || "-h".equals(args[0])) {
          usage(false);
          return;
      } else {
          usage(true);
      }
    }

    // Retrieve all of the modules from each of the components
    FARModules farModules = new FARModules();
    FlowManagerModules flowManagerModules = new FlowManagerModules();
    MetricsModules metricsModules = new MetricsModules();
    GatewayModules gatewayModules = new GatewayModules();
    DataFabricModules dataFabricModules = new DataFabricModules();

    // Set up our Guice injections
    Injector injector = Guice.createInjector(
      farModules.getSingleNodeModules(),
      flowManagerModules.getSingleNodeModules(),
      metricsModules.getSingleNodeModules(),
      gatewayModules.getSingleNodeModules(),
      dataFabricModules.getSingleNodeModules()
    );

    // Create our server instance
    SingleNodeMain continuuity = injector.getInstance(SingleNodeMain.class);

    // Load all our config files
    continuuity.loadConfiguration();

    // Now bootstrap all of the services
    try {
      Copyright.print();
      continuuity.bootStrapServices();
      System.out.println(StringUtils.repeat("=", 80));
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(-1);
    }

  }

} // end of SingleNodeMain class
