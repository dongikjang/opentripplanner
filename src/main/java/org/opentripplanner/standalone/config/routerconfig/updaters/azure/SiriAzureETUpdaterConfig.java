package org.opentripplanner.standalone.config.routerconfig.updaters.azure;

import static org.opentripplanner.standalone.config.framework.json.OtpVersion.NA;

import org.opentripplanner.ext.siri.updater.azure.SiriAzureETUpdaterParameters;
import org.opentripplanner.standalone.config.framework.json.NodeAdapter;

public class SiriAzureETUpdaterConfig extends SiriAzureUpdaterConfig {

  public static SiriAzureETUpdaterParameters create(String configRef, NodeAdapter c) {
    SiriAzureETUpdaterParameters parameters = new SiriAzureETUpdaterParameters();
    populateConfig(parameters, configRef, c);

    if (c.exist("history")) {
      /*TODO DOC*/
      /*TODO DOC*/
      NodeAdapter history = c
        .of("history")
        .withDoc(NA, /*TODO DOC*/"TODO")
        .withDescription(/*TODO DOC*/"TODO")
        .asObject();

      /*TODO DOC*/
      /*TODO DOC*/
      String fromDateTime = history
        .of("fromDateTime")
        .withDoc(NA, /*TODO DOC*/"TODO")
        .asString("-P1D");
      int customMidnight = c.of("customMidnight").withDoc(NA, /*TODO DOC*/"TODO").asInt(0);

      parameters.setFromDateTime(asDateOrRelativePeriod(fromDateTime, customMidnight));
    }

    return parameters;
  }
}
