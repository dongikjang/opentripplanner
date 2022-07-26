package org.opentripplanner.ext.transmodelapi;

import org.opentripplanner.routing.RoutingService;
import org.opentripplanner.standalone.api.OtpServerContext;
import org.opentripplanner.transit.service.TransitService;

public class TransmodelRequestContext {

  private final OtpServerContext serverContext;
  private final RoutingService routingService;
  private final TransitService transitService;

  public TransmodelRequestContext(
    OtpServerContext serverContext,
    RoutingService routingService,
    TransitService transitService
  ) {
    this.serverContext = serverContext;
    this.routingService = routingService;
    this.transitService = transitService;
  }

  public OtpServerContext getServerContext() {
    return serverContext;
  }

  public RoutingService getRoutingService() {
    return routingService;
  }

  public TransitService getTransitService() {
    return transitService;
  }
}
