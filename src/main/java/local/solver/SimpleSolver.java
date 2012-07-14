package local.solver;

import com.owlplatform.common.SampleMessage;
import com.owlplatform.common.util.NumericUtils;
import com.owlplatform.solver.SolverAggregatorConnection;
import com.owlplatform.solver.rules.SubscriptionRequestRule;
import com.owlplatform.worldmodel.Attribute;
import com.owlplatform.worldmodel.solver.SolverWorldConnection;
import com.owlplatform.worldmodel.solver.protocol.messages.AttributeAnnounceMessage.AttributeSpecification;
import com.owlplatform.worldmodel.types.BooleanConverter;

/**
 * A skeleton class for an Owl Platform solver that receives samples from an
 * Aggregator and sends data to a World Model. Every time a sample is received,
 * it sends an update.
 * 
 * @author Robert Moore
 * 
 */
public class SimpleSolver extends Thread {

  /**
   * Expects 4 arguments: aggregator host, aggregator solver port, world model
   * server host, and world model server solver port.
   * 
   * @param args
   *          aggregator host, aggregator port, world model host, world model
   *          port.
   */
  public static void main(String[] args) {
    if (args.length < 4) {
      System.err
          .println("Missing one or more arguments.\nExpecting: <Aggregator Host>, <Aggregator Port>, <WM Host>, <WM Port>");
      return;
    }

    System.out.println("Creating a Simple Solver");

    int aggPort = -1;
    int wmPort = -1;

    try {
      aggPort = Integer.parseInt(args[1]);
    } catch (NumberFormatException nfe) {
      System.err.println("Invalid port number: " + args[1]);
      return;
    }

    try {
      wmPort = Integer.parseInt(args[3]);
    } catch (NumberFormatException nfe) {
      System.err.println("Invalid port number: " + args[3]);
      return;
    }

    // Create a new solver.
    SimpleSolver ss = new SimpleSolver(args[0], aggPort, args[2], wmPort);

    // Try to connect, exiting on failure. May take up to 25 seconds as written.
    if (!ss.connect()) {
      System.err.println("Unable to connect to Owl Platform.");
      return;
    }

    // Launch the solver thread.
    ss.start();
  }

  /**
   * The lowest RSSI value for a device that is "near"
   */
  private static final float RSSI_THRESHOLD = -60f;

  /**
   * The Origin string for sending Attribute values to the world model.
   */
  private static final String ORIGIN = "Example Solver";

  /**
   * The Attribute name to send for this solver. Solvers may send more than one
   * Attribute name, or even a dynamic set of Attributes depending on their
   * functionality.
   */
  private static final String TEST_ATTRIBUTE = "nearby";

  /**
   * Connection to the aggregator. Used to "poll" for samples.
   */
  private final SolverAggregatorConnection agg = new SolverAggregatorConnection();

  /**
   * Connection to the world model. Used to "push" new attribute values.
   */
  private final SolverWorldConnection wm = new SolverWorldConnection();

  /**
   * Creates a new SimpleSolver using the provided connection parameters.
   * 
   * @param aggHost
   *          the aggregator hostname or IP address.
   * @param aggPort
   *          the aggregator port for solver connections.
   * @param wmHost
   *          the world model server hostname or IP address.
   * @param wmClientPort
   *          the world model port for client connections.
   */
  public SimpleSolver(String aggHost, int aggPort, String wmHost,
      int wmClientPort) {
    // Aggregator connection details.
    this.agg.setHost(aggHost);
    this.agg.setPort(aggPort);

    // Set the connection parameters
    this.wm.setHost(wmHost);
    this.wm.setPort(wmClientPort);
    // Set the origin string
    this.wm.setOriginString(ORIGIN);
    /*
     * Attribute types must be announced before being sent. This solver only
     * sends one.
     */
    AttributeSpecification spec = new AttributeSpecification();
    spec.setAttributeName(TEST_ATTRIBUTE);
    spec.setIsOnDemand(false);
    this.wm.addAttribute(spec);
  }

  /**
   * Returns {@code true} if both connections succeed, else {@code false}.
   * 
   * @return {@code true} if both connections succeed.
   */
  public boolean connect() {
    // Try to connect for up to 10 seconds
    if (!this.agg.connect(10000)) {
      System.err.println("Unable to connect to the aggregator.");
      return false;
    }

    if (!this.wm.connect(10000)) {
      this.agg.disconnect();
      System.err.println("Unable to connect to the world model.");
      return false;
    }

    // Try waiting up to 5 seconds for the connection to be ready.
    int waitTimes = 50;
    while (waitTimes-- > 0 && !this.wm.isConnectionLive()) {
      try {
        Thread.sleep(100);
      } catch (InterruptedException ie) {
        // Ignored
      }
    }
    if (waitTimes > 0) {
      return true;
    }
    return false;
  }

  @Override
  public void run() {
    SubscriptionRequestRule everythingRule = new SubscriptionRequestRule();
    everythingRule.setUpdateInterval(0l);
    everythingRule.setPhysicalLayer(SampleMessage.PHYSICAL_LAYER_ALL);
    this.agg.addRule(everythingRule);

    SampleMessage sample = null;
    while ((sample = this.agg.getNextSample()) != null) {
      Attribute attr = new Attribute();
      // If the RSSI value is above threshold, say it's "nearby".
      if (sample.getRssi() > RSSI_THRESHOLD) {
        attr.setAttributeName(TEST_ATTRIBUTE);
        attr.setCreationDate(System.currentTimeMillis());
        attr.setId(NumericUtils.toHexShortString(sample.getDeviceId()));
        attr.setData(BooleanConverter.get().encode(Boolean.TRUE));
      }
      // If the RSSI value is below threshold, say it's "not nearby".
      else {
        attr.setAttributeName(TEST_ATTRIBUTE);
        attr.setCreationDate(System.currentTimeMillis());
        attr.setId(NumericUtils.toHexShortString(sample.getDeviceId()));
        attr.setData(BooleanConverter.get().encode(Boolean.FALSE));
      }
      System.out.println(attr.getId() + ": " + attr);
      // Don't actually send, since that would be a huge waste of data
      // this.wm.updateAttribute(attr);
    }

    System.out.println("No more samples available. Exiting.");

    try {
      this.agg.disconnect();
    } catch (Exception e) {
      // Ignored
    }
    try {
      this.wm.disconnect();
    } catch (Exception e) {
      // Ignored
    }
  }

}
