package org.hl7.davinci.r4.crdhook.orderselect;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.cdshooks.CdsRequest;
import org.hl7.davinci.r4.Utilities;
import org.hl7.davinci.r4.crdhook.CrdPrefetch;

import java.util.HashMap;

public class OrderSelectRequest extends CdsRequest<CrdPrefetch, OrderSelectContext> {

  private HashMap<String, Object> mapForPrefetchTemplates = null;

  /**
   * Gets the data from the context to put into the prefetch token.
   * @return a map of prefetch attributes to their values
   */
  @JsonIgnore
  public Object getDataForPrefetchToken() {
    if (mapForPrefetchTemplates != null) {
      return mapForPrefetchTemplates;
    }
    mapForPrefetchTemplates = new HashMap<>();

    HashMap<String, Object> contextMap = new HashMap<>();
    contextMap.put("userId", getContext().getUserId());
    contextMap.put("patientId", getContext().getPatientId());
    contextMap.put("encounterId", getContext().getEncounterId());
    contextMap.put("selections", getContext().getSelections());
    contextMap.put("draftOrders", Utilities.bundleAsHashmap(getContext().getDraftOrders()));
    mapForPrefetchTemplates.put("context", contextMap);

    return mapForPrefetchTemplates;
  }


}
